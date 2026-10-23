// Copyright (c) 2026 cirokk
// SPDX-License-Identifier: MIT
package io.github.cirokk.mi10t108mp

import android.annotation.TargetApi
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * Original device-specific profile for M2007J3SG / apollo / Android 12.
 * Diagnostic confirmed sensor mode 12032x9024, remosaic, full JPEG and colors.
 * Integration into third-party camera apps is not yet tested. No Xiaomi code/libraries.
 * Host owns CAMERA permission, CameraDevice, worker, session and lifecycle.
 * Use one capture/frame at a time. API 26-28 must use the normal camera path.
 */
object Mi10TPro108MpYuv {
    const val WIDTH = 12032
    const val HEIGHT = 9024
    const val SESSION_MODE = 0x9004
    const val REMOSAIC_TAG = "xiaomi.remosaic.enabled"
    private val remosaicKey = CaptureRequest.Key(REMOSAIC_TAG, Boolean::class.javaObjectType)

    /** Coarse gate only; firmware updates still require validation. */
    fun matchesTestedDevice(): Boolean = Build.MODEL == "M2007J3SG" &&
        Build.DEVICE == "apollo" && Build.VERSION.SDK_INT == 31

    class Outputs internal constructor(
        val preview: ImageReader,
        val full: ImageReader,
        val companion: ImageReader
    ) : Closeable {
        override fun close() {
            runCatching { full.close() }
            runCatching { companion.close() }
            runCatching { preview.close() }
        }
    }

    /**
     * Reproduces the three tested outputs. The PRIVATE preview is drained, not
     * displayed. Replacing it with the app preview surface needs separate testing.
     */
    @TargetApi(29)
    fun createOutputs(worker: Handler): Outputs {
        require(Build.VERSION.SDK_INT >= 29)
        val allocated = mutableListOf<ImageReader>()
        try {
            fun reader(w: Int, h: Int, format: Int, count: Int, zeroUsage: Boolean = false): ImageReader =
                (if (zeroUsage) ImageReader.newInstance(w, h, format, count, 0L)
                else ImageReader.newInstance(w, h, format, count)).also { allocated.add(it) }
            val preview = reader(1920, 1440, ImageFormat.PRIVATE, 3)
            val full = reader(WIDTH, HEIGHT, ImageFormat.YUV_420_888, 1, true)
            val companion = reader(6016, 4512, ImageFormat.YUV_420_888, 1)
            preview.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, worker)
            companion.setOnImageAvailableListener({ r -> runCatching { r.acquireLatestImage()?.close() } }, worker)
            return Outputs(preview, full, companion)
        } catch (e: Throwable) {
            allocated.forEach { runCatching { it.close() } }
            throw e
        }
    }

    fun configureCommon(builder: CaptureRequest.Builder, chars: CameraCharacteristics) {
        val available = chars.availableCaptureRequestKeys.orEmpty().map { it.name }.toSet()
        builder.set(CaptureRequest.CONTROL_ENABLE_ZSL, false)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        for (name in listOf("xiaomi.mfnr.enabled", "xiaomi.swmf.enabled", "xiaomi.hdr.enabled", "xiaomi.superResolution.enabled")) {
            if (name in available) builder.set(CaptureRequest.Key(name, Boolean::class.javaObjectType), false)
        }
        if ("xiaomi.multiframe.inputNum" in available) {
            builder.set(CaptureRequest.Key("xiaomi.multiframe.inputNum", Int::class.javaObjectType), 1)
        }
        // Not advertised in availableCaptureRequestKeys on the tested firmware,
        // but setting is accepted. Preserve the session/preview/still setup.
        builder.set(remosaicKey, true)
    }

    @TargetApi(29)
    fun createSession(
        device: CameraDevice,
        chars: CameraCharacteristics,
        outputs: Outputs,
        worker: Handler,
        callback: CameraCaptureSession.StateCallback
    ) {
        require(Build.VERSION.SDK_INT >= 29)
        require(device.id == "0")
        val executor = Executor { job ->
            if (!worker.post(job)) throw RejectedExecutionException("Camera worker stopped")
        }
        val config = SessionConfiguration(
            SESSION_MODE,
            listOf(outputs.preview, outputs.full, outputs.companion).map { OutputConfiguration(it.surface) },
            executor, callback
        )
        config.sessionParameters = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            configureCommon(this, chars)
        }.build()
        device.createCaptureSession(config)
    }

    fun previewRequest(device: CameraDevice, chars: CameraCharacteristics, outputs: Outputs): CaptureRequest =
        device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(outputs.preview.surface)
            configureCommon(this, chars)
        }.build()

    fun stillRequest(device: CameraDevice, chars: CameraCharacteristics, outputs: Outputs): CaptureRequest =
        device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(outputs.full.surface)
            configureCommon(this, chars)
        }.build()

    /** Approx. 163 MB excluding camera buffers. Release after encoding. */
    class Frame internal constructor(internal val nv21: ByteArray)

    /**
     * Consumes AND closes image, even on failure. After this returns, the host
     * may close session/Outputs before encoding on an I/O worker. Chroma swap is
     * specific to this tested zero-usage path, not generic Android YUV behavior.
     */
    fun takeFrame(image: Image, swapChromaForTestedPath: Boolean = true): Frame = image.use { im ->
        require(im.format == ImageFormat.YUV_420_888)
        require(im.width == WIDTH && im.height == HEIGHT)
        Frame(copyNv21(im, swapChromaForTestedPath))
    }

    /**
     * Full JPEG without scaling. Caller owns/closes stream and handles saving.
     * Does not add EXIF/orientation. JPEG_ORIENTATION on a YUV request is not enough.
     */
    fun writeJpeg(frame: Frame, output: OutputStream, quality: Int = 95) {
        require(quality in 1..100)
        check(YuvImage(frame.nv21, ImageFormat.NV21, WIDTH, HEIGHT, null)
            .compressToJpeg(Rect(0, 0, WIDTH, HEIGHT), quality, output)) { "JPEG encoding failed" }
    }

    // Same original converter exercised in diagnostics 6 and 7.
    private fun copyNv21(image: Image, swapChroma: Boolean = false): ByteArray {
        val w = image.width
        val h = image.height
        require(w % 2 == 0 && h % 2 == 0)
        require(image.cropRect == Rect(0, 0, w, h))
        val out = ByteArray(w * h * 3 / 2)
        for (planeIndex in 0..2) {
            val plane = image.planes[planeIndex]
            val source = plane.buffer.duplicate()
            val origin = source.position()
            val pw = if (planeIndex == 0) w else w / 2
            val ph = if (planeIndex == 0) h else h / 2
            val bytesPerRow = (pw - 1) * plane.pixelStride + 1
            val row = ByteArray(bytesPerRow)
            for (y in 0 until ph) {
                source.position(origin + y * plane.rowStride)
                source.get(row, 0, bytesPerRow)
                if (planeIndex == 0 && plane.pixelStride == 1) {
                    row.copyInto(out, y * w)
                } else {
                    val chromaOffset = if ((planeIndex == 1) xor swapChroma) 1 else 0
                    val offset = if (planeIndex == 0) y * w else w * h + y * w + chromaOffset
                    val step = if (planeIndex == 0) 1 else 2
                    for (x in 0 until pw) out[offset + x * step] = row[x * plane.pixelStride]
                }
            }
        }
        return out
    }
}
