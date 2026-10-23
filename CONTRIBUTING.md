# Contributing

Start with a small, reproducible change. Preserve the tested profile and fallback behavior; add a separate profile for other devices instead of broadening compatibility claims without evidence.

Describe your device/ROM, configuration, actual output dimensions and how you checked color and sensor behavior. Distinguish build checks, synthetic conversion checks and physical camera tests. Do not label output dimensions alone as proof of sensor readout.

Keep contributions original or appropriately licensed. Do not submit OEM APKs, extracted proprietary libraries, copied proprietary source, signing keys, credentials, full phone logs or personal photos. Retain copyright/license notices. Please credit cirokk when using the research in downstream apps or publications, as described in the README.

Build with JDK 17, Android SDK 34 and Gradle 8.9:

```sh
gradle :toolkit:assembleDebug :toolkit:lintDebug
```

Hardware validation remains the contributor's responsibility. Issue reports should use English so other developers can reproduce and compare them.
