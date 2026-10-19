# Third-Party Notices & Acknowledgements

DroidLink integrates several open-source technologies and projects. This project gratefully acknowledges the following upstream software:

---

### 1. Moreland
- **Project**: Moreland (Android tablet as a second monitor on Wayland)
- **Author**: Manav Adhikari (`adiimanav`)
- **Repository**: https://github.com/adiimanav/moreland
- **License**: MIT License
- **Notice**: DroidLink uses Moreland as the display streaming daemon and companion Android SurfaceView receiver, and includes an Intel VA-API DMA-BUF format negotiation patch for Intel GPU support.

---

### 2. Simple Protocol Player
- **Project**: Simple Protocol Player for Android
- **Author**: kaytat
- **Repository**: https://github.com/kaytat/SimpleProtocolPlayer
- **License**: Apache License 2.0
- **Notice**: DroidLink uses Simple Protocol Player as the low-latency TCP PCM audio receiver on Android, automating it via Android Service Intents.

---

### 3. PipeWire & WirePlumber
- **Project**: PipeWire Multimedia Server
- **Project Link**: https://pipewire.org/
- **License**: MIT License / LGPL 2.1+
- **Notice**: Used on Linux for dynamic null-sink creation, virtual audio routing, and TCP PCM streaming via `module-simple-protocol-tcp`.

---

### 4. Hyprland
- **Project**: Hyprland Dynamic Tiling Wayland Compositor
- **Author**: Vaxry
- **Repository**: https://github.com/hyprwm/Hyprland
- **License**: BSD 3-Clause License
- **Notice**: Provides headless virtual display output creation and Wayland DMA-BUF frame export.

---

### 5. Android Debug Bridge (ADB)
- **Project**: Android Open Source Project (AOSP) Platform Tools
- **Project Link**: https://developer.android.com/tools/adb
- **License**: Apache License 2.0
- **Notice**: Provides wired USB transport via forward and reverse port tunneling.
