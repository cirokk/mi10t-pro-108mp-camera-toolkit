# Evidence, scope and unsuccessful paths

## Tested environment

| Item | Value |
|---|---|
| Phone | Xiaomi Mi 10T Pro |
| Model / device | M2007J3SG / `apollo` |
| OS | Android 12, API 31 |
| ROM | MIUI 13.0.8.0.SJDEUVF, EEA |
| Rear main camera | Camera2 ID `0`; sensor reported as `apollo_s5khmx` |
| Method | Original diagnostic app with CAMERA permission, without root or system-property changes |

These are compatibility details, not a serial number or unique device identifier. Raw captures, logcat, bugreports, OEM APKs and extracted libraries are intentionally not published.

## Evidence ladder

1. **Requested dimensions:** what the app asks for. Not proof of delivered dimensions.
2. **Delivered dimensions:** inspected YUV planes or JPEG header. Not proof of sensor readout.
3. **Decoded image:** confirms a complete photographic output, not optical resolving power.
4. **Capture-time sensor/pipeline evidence:** establishes which sensor readout and processing path executed.
5. **Controlled detail measurement:** requires matched scene, framing, focus, exposure and processing. Not completed here.

The working YUV path reached levels 3 and 4 across diagnostic runs. Level 5 remains open.

## Results

| Experiment | Observed result | Interpretation |
|---|---|---|
| Earlier full-size JPEG request with ordinary reader usage | Output reduced to roughly 1920-pixel width despite the requested size | Reader properties do not establish actual output size |
| JPEG session `0x80f3`, zero usage, remosaic on/off | 12032×9024 JPEGs; inspected runs selected BPS sensor mode index 1 (6016×4512) | Full-size JPEG alone did not confirm full sensor readout |
| YUV session `0x9004`, remosaic from session setup | Full YUV delivered; `ProcessSWRemosaicSnapshot`, sensor mode 0, internal RAW 12032×9024 and BPS mode index 0 observed during capture | Evidence of the full-resolution sensor path in those runs |
| Direct RAW_SENSOR with intermediate `0x80f3` setup | Configuration rejected, error -38 (`Function not implemented`) | No supported full-resolution app RAW/DNG path established |
| Diagnostic 6 conversion | Full image but visibly incorrect colors | Conversion needed a profile-specific chroma correction |
| Diagnostic 7, same YUV capture configuration with chroma swap | JPEG 12032×9024, 26,652,971 bytes; complete decode and visual color check | Working full-size encoded output with corrected colors |
| Diagnostic 7 control | JPEG 6016×4512, 10,403,913 bytes; complete decode | Comparison output, not a controlled optical-resolution experiment |

**Timing boundary:** sensor-mode evidence belongs to earlier YUV diagnostics (5/6). The live collector ended before the diagnostic 7 shots. Diagnostic 7 retained the capture configuration and changed the chroma conversion; do not describe it as having its own simultaneously recorded sensor-mode trace.

The mode table identified sensor mode 0 as 12032×9024 at 10 fps. This does **not** imply 10 fps full-resolution JPEG throughput: conversion, processing and storage add cost.

## Why `usage=0L` matters here

Local investigation of the tested firmware found surface-size substitution for some consumer configurations. The public ImageReader overload accepting usage, supplied with zero, avoided the previously observed size substitution in these tests. This is an empirical firmware behavior, not a portable guarantee for arbitrary format/usage combinations. Keep it device-gated.

## Color finding

A synthetic on-device NV21 encoder test behaved as expected. The observed color reversal was corrected by swapping the two source chroma planes when constructing NV21 for this specific YUV/zero-usage path. The helper respects row/pixel stride and starting buffer positions and does not resize the image. The Android standard plane order is not redefined by this workaround.

## What this does not prove

- Universal Xiaomi, custom ROM or GCam support.
- Four times more useful detail than 27 MP, or better low-light quality.
- Full-resolution app-accessible RAW, DNG, burst, ZSL, HDR+ or video.
- A visible SurfaceTexture preview in the special configuration.
- That any one setting alone is sufficient or that all included settings are individually necessary.

The high-resolution sensor path and a good final photograph are related but different questions. Binning reduces spatial resolution while combining sensor samples; it is not four separate 27 MP photos assembled to create the 108 MP mode.

## References and prior work

- [Android ImageReader](https://developer.android.com/reference/android/media/ImageReader) — API overloads, image ownership and usage.
- [SessionConfiguration](https://developer.android.com/reference/android/hardware/camera2/params/SessionConfiguration) — session setup; it does not document Xiaomi's `0x9004` meaning.
- [AOSP Android 12 surface configuration code](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android12-release/services/camera/libcameraservice/utils/SessionConfigurationUtils.cpp) — framework context; vendor behavior still needs device evidence.
- [Samsung ISOCELL Bright HMX](https://semiconductor.samsung.com/image-sensor/mobile-image-sensor/isocell-bright-hmx/) — sensor background.
- [Wyroczen's Open Camera publication](https://www.celsoazevedo.com/files/android/open-camera/dev-wyroczen/f/dl0/) and [AlphaCamera](https://github.com/Wyroczen/AlphaCamera) — prior public remosaic work; no claim of being the first to explore Xiaomi high-resolution capture.

No code or binaries from those apps were copied into this toolkit. Public interfaces, observed behavior and original implementation are distinguished from proprietary application contents.
