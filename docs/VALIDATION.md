# Validation checklist

## Repository validation

The original helper was compiled against Android SDK 34 with Kotlin 1.9.24. Its actual converter was exercised with 16 synthetic cases covering row padding, Y/chroma pixel strides, nonzero buffer positions, minimally sized last rows and both chroma orders. These are conversion checks, not hardware tests.

The published source changes the package name and attribution comments; it preserves the tested capture behavior. CI builds and lints the Android library. Check the workflow result on your revision rather than assuming a hardware test was performed in CI.

## On-device port acceptance

- [ ] Record model, Android version and ROM release (no serial number).
- [ ] Confirm normal camera mode still works before and after using this mode.
- [ ] Confirm special session setup, image arrival and a fully decoded 12032×9024 output.
- [ ] Test a known color chart, orientation and EXIF.
- [ ] Measure capture/conversion/save latency and peak memory; avoid queued frames.
- [ ] Exercise cancel/pause/back during configuration, exposure, acquisition and encoding.
- [ ] Exercise configuration failure, image timeout and save failure; confirm cleanup/fallback.
- [ ] Capture repeatedly without accumulating readers, images or pending media rows.
- [ ] If claiming full sensor readout on new firmware, collect capture-time sensor-mode evidence privately and publish only a sanitized summary.
- [ ] If claiming extra detail, match focus, framing, exposure, scene and processing to a 27 MP control. Pixel count alone is insufficient.

## Reporting evidence responsibly

Use the compatibility issue template. A generic result such as “decoded JPEG 12032×9024; color check passed; sensor mode not inspected” is more useful than an overstated “108 MP unlocked everywhere.”

Do not upload full phone logs, bugreports, IMEI/serials, Bluetooth identifiers, account details, location, personal images or image EXIF containing private information. Public diagrams and synthetic fixtures are preferable. Share only the minimal sanitized information relevant to the camera behavior.
