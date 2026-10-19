# Third-Party Notices & Acknowledgements

Droid Link integrates and builds upon several open-source technologies. This document provides legal attribution and copyright notices for third-party software incorporated or adapted within this repository.

---

### 1. Droid Link Display Client (`android/display/`)
- **Upstream Project**: Moreland Android Client
- **Original Author**: Manav Adhikari (`adiimanav`)
- **Upstream Repository**: https://github.com/adiimanav/moreland
- **Licence**: Apache License, Version 2.0 / MIT License
- **Modifications**:
  - Re-namespaced to `io.github.rayhan099.droidlink.display`
  - Re-branded application label to "Droid Link Display"
  - Added original vector launcher icon and modern dark theme
  - Integrated into unified multi-module Gradle build
  - Maintained wire-compatibility with Moreland Wayland daemon via abstract UNIX domain socket `moreland`

---

### 2. Droid Link Audio Client (`android/audio/`)
- **Upstream Project**: Simple Protocol Player for Android
- **Original Author**: kaytat
- **Upstream Repository**: https://github.com/kaytat/SimpleProtocolPlayer
- **Licence**: Apache License, Version 2.0
- **Modifications**:
  - Re-namespaced to `io.github.rayhan099.droidlink.audio`
  - Re-branded application label to "Droid Link Audio"
  - Added original vector launcher icon
  - Integrated into unified multi-module Gradle build
  - Enhanced `MusicService` to handle both Droid Link (`io.github.rayhan099.droidlink.audio.action.*`) and legacy (`com.kaytat.simpleprotocolplayer.action.*`) intent actions
  - Upgraded dependencies to AndroidX Media3 1.4.1

---

### 3. Moreland Display Streaming Daemon & Patches
- **Project**: Moreland (Wayland display streaming daemon)
- **Author**: Manav Adhikari (`adiimanav`)
- **Repository**: https://github.com/adiimanav/moreland
- **Licence**: MIT License
- **Notice**: Droid Link utilizes Moreland for Wayland virtual display creation and DMA-BUF frame capture, providing an Intel VA-API DMA-BUF format negotiation patch and client auto-discovery for `io.github.rayhan099.droidlink.display`.

---

### 4. PipeWire & WirePlumber
- **Project**: PipeWire Multimedia Server
- **Project Link**: https://pipewire.org/
- **Licence**: MIT License / LGPL 2.1+
- **Notice**: Used on Linux for dynamic null-sink creation, virtual audio routing, and TCP PCM streaming via `module-simple-protocol-tcp`.

---

### 5. Hyprland
- **Project**: Hyprland Dynamic Tiling Wayland Compositor
- **Author**: Vaxry
- **Repository**: https://github.com/hyprwm/Hyprland
- **Licence**: BSD 3-Clause License
- **Notice**: Provides headless virtual display output creation and Wayland DMA-BUF frame export.

---

### 6. Android Debug Bridge (ADB)
- **Project**: Android Open Source Project (AOSP) Platform Tools
- **Project Link**: https://developer.android.com/tools/adb
- **Licence**: Apache License, Version 2.0
- **Notice**: Provides wired USB transport via forward and reverse port tunneling.
