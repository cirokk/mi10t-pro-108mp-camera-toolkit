# Troubleshooting

| Symptom | Check first |
|---|---|
| JPEG is about 1920 pixels wide | Inspect the actual JPEG header, not reader properties. Confirm zero usage on the full reader and the complete tested stream/session setup. |
| JPEG is 108 MP but detail looks like 27 MP | Dimensions do not prove sensor readout. Compare capture-time sensor mode evidence; the direct JPEG `0x80f3` experiment was insufficient. |
| Yellow/cyan or red/blue look swapped | Verify the profile-specific chroma swap, buffer offsets and strides. Use a known color chart. Do not flip channels globally. |
| `onConfigureFailed` / unsupported session | Confirm device, ROM, camera 0 and all three outputs. Fall back to the normal camera session. Do not loop indefinitely. |
| Remosaic key absent from advertised keys | The tested firmware accepted the explicit Boolean key despite not advertising it. Absence alone is not the test used here; still catch rejection on other firmware. |
| No image after capture completion | Completion metadata is not image arrival. Install listener before setup, consume readers and use an image timeout. |
| Preview black/frozen during capture | The validated PRIVATE preview is discarded. Show a capture indicator or frozen previous frame; displayed preview replacement needs separate validation. |
| `Function not implemented` (-38) for RAW | The tested direct RAW path failed. Internal sensor RAW is not proof of app-accessible RAW. Use the validated YUV path. |
| App runs out of memory | One NV21 frame is ~155.3 MiB. Avoid full Bitmaps, copies and bursts; close images/readers and release frame references promptly. |
| Rotated output or missing metadata | Add EXIF in the host save pipeline. `YuvImage` does not inherit Camera2 EXIF. Avoid double rotation. |
| App freezes while saving | Conversion, encoding and I/O must run off the main thread. Only UI notifications go to the main thread. |
| Old callbacks reopen the camera after pause | Use capture-generation tokens and serialize cleanup with image acquisition. |

Keep the initial port conservative: do not remove streams, change tag types, add HDR or substitute the preview output while diagnosing another failure. Change one variable at a time and retain a working fallback.

Do not change system properties, install a root module, spoof another app's identity or add proprietary OEM libraries to reproduce the published path. None was needed in the tested setup.
