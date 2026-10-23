# Notes for GCam mod developers

**No GCam build has been validated with this toolkit.** This is a Camera2 capture recipe and original adapter, not a ready-made GCam XML, Magisk module, patched APK or universal unlock.

## What a port needs to change

If you maintain a mod and can change its capture code, add a separate device-specific still mode first:

1. Gate on the tested device/firmware; retain normal photo mode as fallback.
2. Find the actual owner of camera ID 0 and its capture-session lifecycle. Close the existing session before constructing the special one.
3. Create the full-resolution YUV ImageReader with the API 29 overload and `usage=0L`. Merely adding 12032×9024 to a size menu is insufficient.
4. Configure session mode `0x9004` with PRIVATE 1920×1440, full YUV 12032×9024 and companion YUV 6016×4512.
5. Apply the tested request settings to session parameters, preview and still requests, including the Boolean Xiaomi remosaic tag. Keep the full-resolution image as the only still target.
6. Start with a **single-frame YUV → JPEG path** using this adapter. Handle buffer ownership, memory, color order, metadata and cancellation.
7. Restore the mod's normal session afterward. Verify the output and sensor path before considering integration into additional processing modes.

If the mod uses a native capture pipeline, adapt the behavior at that boundary. Copying a Kotlin object alone will not change native session construction.

## Why an XML or library setting may not be enough

A configuration file can only control options that the mod already implements. A size override or remosaic toggle does not automatically change the session type, ImageReader usage, auxiliary stream layout, conversion or processing pipeline. No universal configuration keys are specified here because no particular mod's implementation has been validated.

## HDR+, Night Sight and RAW

The validated output is a processed YUV still, encoded to JPEG in the app. The driver's internal RAW does not mean a full-resolution RAW buffer is available to the app. Direct RAW capture in the tested intermediate configuration failed with error -38.

Do not feed this YUV result into code expecting RAW Bayer data, assume compatibility with a multi-frame HDR+ merge, or claim that Night Sight, ZSL, burst or video inherit this support. Those features require their own stream layouts, processing contracts and tests. The tested requests disable ZSL and selected vendor multi-frame features.

## Compatibility reports that help

Include mod name/version, device and ROM version, whether the session configured, actual decoded JPEG dimensions, color results, capture latency and memory behavior. Mark sensor readout as unverified unless supported by capture-time evidence. Do not attach proprietary app code, private photos or raw device logs. A configuration that produces a 108 MP-sized file alone is not enough to claim full sensor readout.

See [the generic integration contract](INTEGRATION.md), [evidence](EVIDENCE.md), and [credit request](../README.md#please-credit-this-work).
