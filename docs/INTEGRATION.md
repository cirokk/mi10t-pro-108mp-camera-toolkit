# Integrating into a Camera2 app

This guide targets developers who control their app's capture-session lifecycle. It is not an APK patch, a system setting, or a generic high-resolution flag.

## 1. Add the adapter

Copy `toolkit/src/main/java/io/github/cirokk/mi10t108mp/Mi10TPro108MpYuv.kt` into your source tree, retaining its license notice. Alternatively, include `:toolkit` in your Gradle settings and use `implementation(project(":toolkit"))`.

No extra runtime library is needed. The host may retain minSdk 26 but must guard this path behind API 29+. Compilation uses SDK 34. The tested device is API 31; API 29 availability is not a compatibility claim for all API 29 phones.

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Request CAMERA at runtime. Internal app storage requires no storage permission. For gallery export on the tested Android 12 device, use MediaStore with a pending row. Do not request all-files access.

## 2. Offer an explicit device-specific photo mode

`matchesTestedDevice()` checks model, device codename and API level. It is only a coarse gate: it does not compare the full firmware. Record the tested ROM in your compatibility table and expose this as an experimental mode until your integration passes validation.

Use an already-open rear main `CameraDevice` with ID `0`. Do not open another camera in parallel. Serialize transitions on the app's camera worker:

1. Reject a new capture if a prior image is still being processed.
2. Stop and close the normal session, retaining the device if your controller permits.
3. Freeze the last normal preview frame or show a capture-state UI. The validated special preview is consumed and discarded; it is not a displayed SurfaceTexture.
4. Allocate the three outputs and install the full-image listener before creating the special session.
5. Configure the session, repeat preview, settle AF/AE, and submit one still request targeting only `outputs.full`.
6. Acquire/copy the full image, retire the temporary session and readers, encode/save, then restore ordinary preview if the app is still active.

## 3. Session and request wiring

The following is a wiring excerpt for an existing camera controller, not a standalone Activity. The named host callbacks are responsibilities of your controller, described below.

```kotlin
// cameraHandler: Handler belonging to the camera worker.
// device: already-open camera ID 0. characteristics: its characteristics.
// token: the current lifecycle/capture generation.
val outputs = Mi10TPro108MpYuv.createOutputs(cameraHandler)

outputs.full.setOnImageAvailableListener({ reader ->
    val image = reader.acquireNextImage() ?: return@setOnImageAvailableListener
    if (!isCurrentCapture(token)) {
        image.close()
        return@setOnImageAvailableListener
    }
    try {
        // Takes ownership of Image and closes it even when conversion fails.
        val frame = Mi10TPro108MpYuv.takeFrame(image)
        onFrameCopied(token, frame) // Retire this session/readers, then encode off-thread.
    } catch (failure: Exception) {
        failAndRestore(token, failure)
    } catch (failure: OutOfMemoryError) {
        failAndRestore(token, failure)
    }
}, cameraHandler)

Mi10TPro108MpYuv.createSession(
    device, characteristics, outputs, cameraHandler,
    object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            if (!isCurrentCapture(token)) {
                session.close()
                outputs.close()
                return
            }
            // Store session and outputs under this capture token for cleanup.
            ownTemporarySession(token, session, outputs)
            session.setRepeatingRequest(
                Mi10TPro108MpYuv.previewRequest(device, characteristics, outputs),
                previewResults, cameraHandler
            )
            // Once AF/AE is ready (or a bounded settle policy expires):
            // session.capture(
            //     Mi10TPro108MpYuv.stillRequest(device, characteristics, outputs),
            //     stillResults, cameraHandler
            // )
        }
        override fun onConfigureFailed(session: CameraCaptureSession) {
            session.close()
            outputs.close()
            failAndRestore(token, IllegalStateException("108 MP configuration rejected"))
        }
    }
)
```

Also catch exceptions thrown synchronously by output allocation, session creation, repeating requests and still submission. Register output ownership immediately after allocation, so cancellation before `onConfigured` cannot leak readers. Never close readers from another thread while `takeFrame` is copying; serialize that operation and cleanup on the camera worker.

The diagnostic used approximately 1.5 seconds of preview before capture. Production code should observe AF/AE results with a bounded timeout and offer cancellation. Reuse neither stale AF state nor exposure results from a previous session as proof that the new session is ready.

## 4. Lifecycle contract

Implement the host callbacks in the excerpt with these rules:

| Responsibility | Required behavior |
|---|---|
| `isCurrentCapture(token)` | False after pause, cancellation, disconnect or replacement capture |
| `ownTemporarySession` | Store session/readers by generation; do not overwrite newer resources from a stale callback |
| `onFrameCopied` | Cancel the image timeout; retire temporary resources; keep the capture busy until encoding and publication finish |
| `failAndRestore` | Idempotent cleanup; delete only this capture's incomplete output; restore normal preview only if the app is active |
| `previewResults` | Observe AF/AE; permit at most one still request |
| `stillResults` | Handle `onCaptureFailed`, aborted sequences and camera errors; retain capture metadata for EXIF |

Use a bounded overall/image-arrival timeout; 20 seconds was used by the diagnostic, not established as a universal latency guarantee. `onCaptureCompleted` is not confirmation that an image was delivered or saved. Wait for the image listener and successful publication.

When paused, invalidate the token, cancel scheduled work, stop/close the temporary session and readers, and close or retain the device according to the host's established policy. A background encoder must check cancellation before publishing. Do not allow a queued completion to revive a paused camera.

## 5. Encode and save

```kotlin
// On an I/O worker, after copying and closing the Image:
destination.outputStream().use { stream ->
    Mi10TPro108MpYuv.writeJpeg(frame, stream, quality = 95)
}
// Add orientation and desired capture EXIF, then atomically publish.
// Post success to the main thread only after all steps succeed.
```

The host owns `destination`. For MediaStore, insert with `IS_PENDING=1`, write through `openOutputStream`, add metadata, then set `IS_PENDING=0`. On error/cancellation, delete only the row created for this capture. For internal files, write to a new temporary file and rename on success. Keep output names and paths under the host's control.

`JPEG_ORIENTATION` on a YUV request does not rotate or annotate the subsequently encoded JPEG. Use the host's existing EXIF implementation; if it has none, choose an appropriate metadata writer separately. No EXIF library is bundled in this adapter. Match capture metadata to the image timestamp; apply rotation exactly once.

## 6. Memory and color

One NV21 frame is 162,865,152 bytes. The camera and encoder need additional memory. Do not allocate a full-size ARGB Bitmap (~414 MiB), copy the entire frame unnecessarily, or queue a burst. The diagnostic enabled `largeHeap`; that is not a memory guarantee or a substitute for bounded allocation. Catch allocation failure, release resources and fall back cleanly.

The tested `usage=0L` profile required a chroma-plane swap when packing NV21. `takeFrame` defaults to that behavior. The converter handles row stride, pixel stride and buffer starting positions and requires the full crop. Validate color charts on any new ROM; do not make this workaround the default for every camera in your app.

## 7. Any camera app?

Any app with sufficient control over its Camera2 backend can attempt this integration on a supported device. It cannot be injected into an arbitrary installed app without modifying that app's capture implementation. CameraX users should isolate a Camera2 mode and manage camera ownership carefully; this repository does not supply a CameraX adapter. NDK/native backends need equivalent stream, session and metadata support; no native binding is supplied.

The first integration should preserve all three outputs, all tested tags and the drained PRIVATE preview. Replacing output surfaces, removing the companion stream, adding HDR or combining this with recording changes the configuration and requires new tests.
