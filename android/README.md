# Droid Link Android Companion Applications

This directory contains the first-party Android companion applications for **Droid Link**, structured as a multi-module Gradle project:

| Module | Application Name | Application ID | Role | Upstream Foundation |
| :--- | :--- | :--- | :--- | :--- |
| `:display` | **Droid Link Display** | `io.github.rayhan099.droidlink.display` | Hardware-accelerated H.264 video rendering surface | Forked & maintained from [Moreland](https://github.com/adiimanav/moreland) (Apache-2.0) |
| `:audio` | **Droid Link Audio** | `io.github.rayhan099.droidlink.audio` | Low-latency TCP PCM audio streaming receiver | Forked & maintained from [SimpleProtocolPlayer](https://github.com/kaytat/SimpleProtocolPlayer) (Apache-2.0) |

Both applications are built from source reproducibly using Gradle 8.11.1, Android Gradle Plugin 8.7.3, and Kotlin 2.0.21.

---

## 1. Project Architecture & Attribution

Droid Link does not redistribute prebuilt third-party binaries under borrowed identity. Instead, both clients are maintained as source modules directly in this repository with original branding, launcher icons, package namespaces, and backward-compatible protocol contracts:

- **Attribution & Licences**: Both original upstream projects are licensed under the **Apache License, Version 2.0**. Full legal notices and copyright statements are preserved in the source headers and documented in [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).
- **Display Protocol**: Uses abstract UNIX domain socket `moreland` over ADB port forwarding (`protocol.SOCKET_NAME = "moreland"`), retaining 100% wire-compatibility with the Wayland capture daemon.
- **Audio Protocol**: Receives raw 48 kHz 16-bit stereo PCM over TCP port 4711 via `adb reverse`. `MusicService` responds to both `io.github.rayhan099.droidlink.audio.action.PLAY/STOP` and legacy `com.kaytat.simpleprotocolplayer.action.PLAY/STOP` intents.

---

## 2. Reproducible Build Instructions

### Prerequisites

- **JDK**: Java Development Kit 17 (e.g. OpenJDK 17)
- **Android SDK**: API Level 34 (`android-34`) and Build-Tools `34.0.0` or `35.0.0`

### Build Commands

From the `android/` root directory:

```bash
# Set environment variables for the SDK and JDK
export ANDROID_HOME="/path/to/android-sdk"
export JAVA_HOME="/path/to/jdk-17"

# Build release APKs for both display and audio modules
./gradlew assembleRelease
```

### Build Outputs

Upon completion, the signed release APKs are generated at:

- **Display**: `android/display/build/outputs/apk/release/display-release.apk`
- **Audio**: `android/audio/build/outputs/apk/release/audio-release.apk`

> [!NOTE]
> The `release` build configuration uses debug-compatible v2 signature configuration out-of-the-box so that no secret keystores or credentials are required or checked into the Git repository. The resulting APKs can be installed and tested immediately on any Android device.

---

## 3. Sideloading & Installation

### Standard ADB Installation

When connected via USB with Developer Options and USB Debugging enabled:

```bash
# Install Droid Link Display
adb install -r android/display/build/outputs/apk/release/display-release.apk

# Install Droid Link Audio
adb install -r android/audio/build/outputs/apk/release/audio-release.apk
```

### Xiaomi / Redmi (MIUI & HyperOS) Notice

Xiaomi devices running MIUI or HyperOS enforce security restrictions on `adb install` by default (requiring a SIM card and Mi Account to toggle "Install via USB").

If "Install via USB" cannot be toggled directly:

1. Push the APKs to the tablet's internal storage:
   ```bash
   adb push android/display/build/outputs/apk/release/display-release.apk /sdcard/Download/
   adb push android/audio/build/outputs/apk/release/audio-release.apk /sdcard/Download/
   ```
2. On your tablet, open the native **File Manager** app.
3. Open the **Download** folder.
4. Tap each APK (`display-release.apk` and `audio-release.apk`) to install.
5. Grant required permissions when prompted.

---

## 4. Automation & Service Intent Contract

The host CLI scripts (`tablet` and `tablet-audio`) communicate with the apps headlessly via ADB:

### Display Client
- **Launch Activity**:
  ```bash
  adb shell am start -n io.github.rayhan099.droidlink.display/.DisplayActivity
  ```
- **Force Stop**:
  ```bash
  adb shell am force-stop io.github.rayhan099.droidlink.display
  ```

### Audio Receiver
- **Start Foreground Service**:
  ```bash
  adb shell am start-foreground-service \
    -a io.github.rayhan099.droidlink.audio.action.PLAY \
    --es ip_addr "127.0.0.1" \
    --ei audio_port 4711 \
    --ei sample_rate 48000 \
    --ez stereo true \
    --ez retry true \
    --ez use_performance_mode true \
    --ez use_min_buffer true \
    io.github.rayhan099.droidlink.audio/.MusicService
  ```
- **Stop Service**:
  ```bash
  adb shell am start-service \
    -a io.github.rayhan099.droidlink.audio.action.STOP \
    io.github.rayhan099.droidlink.audio/.MusicService
  ```

---

## 5. Backward Compatibility Guarantee

To guarantee that existing working installations are not broken:
- Host scripts automatically check for `io.github.rayhan099.droidlink.audio` first; if absent, they seamlessly fall back to `com.kaytat.simpleprotocolplayer`.
- The display capture daemon checks for `io.github.rayhan099.droidlink.display` first; if absent, it falls back to `com.moreland.display`.
- `Droid Link Audio` accepts both `io.github.rayhan099.droidlink.audio.action.*` and `com.kaytat.simpleprotocolplayer.action.*` intent actions.
