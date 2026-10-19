# DroidLink

> Turn an Android tablet into a high-performance extended desktop monitor and dedicated external audio endpoint over a single USB cable on Linux (CachyOS / Hyprland).

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Linux%20%7C%20Wayland%20%7C%20Hyprland-brightgreen.svg)]()
[![Audio](https://img.shields.io/badge/Audio-PipeWire%20%7C%20WirePlumber-orange.svg)]()

**DroidLink** is an integration suite engineered by [Rayhan Khan](https://github.com/Rayhan-099) that seamlessly bridges Linux (Wayland / Hyprland) with Android tablets. It provides an **independent extended desktop display** (not screen mirroring) with hardware-accelerated video encoding, alongside **ultra-low latency PCM audio streaming** routed directly to the tablet's hardware speakers, backed by automatic fail-safe audio restoration.

---

## Features

- **Genuine Extended Desktop**: Creates an independent Wayland workspace on the tablet (tested at `1920×1200@90Hz` scaled to native `2560×1600`), allowing separate windows to be moved freely across displays.
- **Hardware-Accelerated H.264 Video**: Zero-copy DMA-BUF capture via Intel VA-API (`vah264enc`) and AMD VCN with runtime dynamic adaptive bitrate scaling (5–30 Mbps).
- **Wayland Touch & Stylus Input**: Full stylus pressure, tilt, barrel buttons, and multi-touch redirection from Android's digitizer directly into Hyprland via virtual uinput devices mapped to the extended monitor.
- **Picture-in-Picture & Surface Lifecycle**: Android Display client supports dynamic surface re-binding and native Picture-in-Picture, preventing stream drops when switching apps.
- **Ultra-Low Latency USB Audio**: Pure TCP streaming (48kHz 16-bit stereo PCM) with `< 20ms` latency directly through ADB reverse port forwarding.
- **Automatic Audio Restoration**: Automatically saves your active sound card before routing to the tablet and restores it instantaneously when streaming stops or the cable is unplugged.
- **Background Disconnect Watchdog**: Real-time non-busy monitor that prevents audio dropouts or zombie virtual devices upon unexpected disconnection.
- **Multi-Tablet Discovery**: Automatic single-device resolution, explicit serial targeting (`-d / --device <SERIAL>`), and isolated state per device.
- **Waybar & Desktop Integration**: Fast, non-blocking custom Waybar status module (`tablet-waybar`) with click-to-toggle actions and `.desktop` application launchers.
- **Single Cable Convenience**: Video, audio, and input streams multiplexed over a single standard USB-C data cable.
- **Clean User-Space Tooling**: No kernel modules, zero root privileges required during runtime, and zero modification to your system audio configuration.

---

## Architecture

```mermaid
flowchart LR
    subgraph Host["Linux Host (CachyOS / Hyprland)"]
        direction TB
        subgraph DisplaySubsystem["Display & Video Pipeline"]
            H_Disp["Hyprland Virtual Output\n(moreland: 1920x1200@90)"]
            DMABUF["Zero-Copy DMA-BUF Export\n(AR24 Tiled)"]
            VA["Intel VA-API / vapostproc\n(vah264enc H.264)"]
            MorelandHost["Moreland Daemon\n(Adaptive Bitrate Controller)"]
            H_Disp --> DMABUF --> VA --> MorelandHost
        end

        subgraph AudioSubsystem["Audio Pipeline"]
            Apps["Audio Apps (Browser, Media, Games)"]
            PW["PipeWire Sound Server"]
            Sink["Virtual Sink (Tablet_Audio)"]
            SimpleTCP["module-simple-protocol-tcp\n(Port 4711, 48kHz Stereo)"]
            Watchdog["tablet-audio-watchdog\n(Background ADB Monitor)"]
            Apps --> PW --> Sink --> SimpleTCP
            Watchdog -. Auto-Restores .-> PW
        end

        subgraph InputSubsystem["Input Pipeline"]
            UInput["droidlink-input Daemon\n(/dev/uinput virtual devices)"]
            HyprInput["Hyprland Input Engine\n(Mapped to moreland output)"]
            UInput --> HyprInput
        end
    end

    subgraph USB["USB Transport (Single USB-C Cable)"]
        ADB_Fwd["ADB Forward\n(localabstract:moreland)"]
        ADB_Rev["ADB Reverse\n(tcp:4711 -> tcp:4711)"]
        ADB_Inp["ADB Reverse\n(tcp:4820 -> tcp:4820)"]
    end

    subgraph Tablet["Android Tablet (Redmi Pad)"]
        direction TB
        DisplayApp["Droid Link Display\n(SurfaceView / H.264 / PiP)"]
        TouchClient["TouchClient\n(Stylus & Multitouch Engine)"]
        AudioApp["Droid Link Audio\n(MusicService / AudioTrack)"]
        Screen["Extended Monitor Display"]
        Speakers["Tablet Speakers"]

        DisplayApp --> Screen
        DisplayApp --> TouchClient
        AudioApp --> Speakers
    end

    MorelandHost === ADB_Fwd === DisplayApp
    TouchClient === ADB_Inp === UInput
    SimpleTCP === ADB_Rev === AudioApp
```

Video, audio, and input pipelines operate as **completely independent streams**: you can toggle audio on and off without flickering or interrupting the extended desktop, and input events inject seamlessly into your compositor.

---

## Quick Start

```bash
# 1. Start the extended display
tablet

# 2. Route desktop audio to tablet speakers
tablet audio

# 3. Check live connection, display, audio, and device status
tablet status

# 4. Stop audio and restore laptop speakers
tablet audio stop

# 5. Stop display and clean up
tablet stop
```

---

## Command Reference

| Command | Action |
| :--- | :--- |
| `tablet` *(or `tablet start`)* | Starts the Moreland extended display service only. Laptop audio remains unaffected. |
| `tablet -d <SERIAL> start` | Targets a specific tablet serial when multiple Android devices are connected. |
| `tablet devices` | Lists all connected ADB devices, authorizations, models, and active DroidLink streams. |
| `tablet doctor` | **Full-Stack Diagnostics**: Audits GPU/VA-API, PipeWire, ADB, uinput, Hyprland, and APK status. |
| `tablet audio` *(or `tablet audio start`)* | Starts tablet audio streaming, launches Android receiver, routes PC audio, and starts the watchdog. |
| `tablet audio stop` | Stops audio streaming, shuts down Android player, and **restores laptop audio to your saved default sink**. |
| `tablet input start` | Starts the `droidlink-input` uinput daemon for tablet touch and stylus redirection into Hyprland. |
| `tablet input stop` | Stops the touch/stylus input bridge daemon. |
| `tablet stop` | **All-in-one cleanup**: Stops input and audio (if active), restores laptop audio, and shuts down the extended display service. |
| `tablet status` | Reports live status of the display daemon, ADB USB connection, audio stream, and active default sink. |
| `tablet restart` | Restarts the Moreland display service without disturbing active audio routing. |
| `tablet-waybar status` | Emits JSON formatted for Waybar custom modules (`{"text": "...", "class": "..."}`). |

---

## Waybar & Desktop Integration

DroidLink includes an ultra-fast Waybar module for monitoring and toggling display/audio directly from your status bar:

```jsonc
// Add to your ~/.config/waybar/config.jsonc:
"custom/droidlink": {
    "exec": "tablet-waybar status",
    "return-type": "json",
    "interval": 2,
    "on-click": "tablet-waybar toggle-display",
    "on-click-right": "tablet-waybar toggle-audio",
    "on-click-middle": "tablet-waybar stop-all",
    "tooltip": true
}
```

Sample configuration and stylesheets are located in [`examples/waybar/`](examples/waybar/).

---

## Prerequisites & Supported Environments

### Tested Hardware & Environment
- **Operating System**: CachyOS / Arch Linux (x86_64, Linux kernel 7.2+)
- **Compositor**: Hyprland 0.50+ (Wayland)
- **Audio Server**: PipeWire 1.0+ with WirePlumber
- **GPU**: Intel Iris Plus Graphics G1 / Intel Xe / AMD Radeon (VA-API H.264 supported)
- **Tablet**: Redmi Pad (Android 14 / Xiaomi HyperOS 1.0, 2560×1600 resolution)
- **Connection**: USB-C 3.0 / 3.1 data cable

### Dependencies
Install the required tools through `pacman`:

```bash
sudo pacman -S android-tools pulseaudio-utils gstreamer gst-plugins-base gst-plugins-good gst-plugins-bad gst-plugin-va python
```

---

## Installation

### 1. Clone DroidLink
```bash
git clone https://github.com/Rayhan-099/droid-link.git
cd droid-link
```

### 2. Configure uinput Permissions (For Touch & Stylus)
To allow non-root injection of virtual touch and pen events into Wayland:
```bash
sudo ./scripts/setup-uinput.sh
```

### 3. Run the Installer
```bash
./scripts/install.sh
```
This installs all CLI utilities (`tablet`, `tablet-audio`, `tablet-audio-watchdog`, `droidlink-device`, `droidlink-input`, `tablet-waybar`) into `~/.local/bin`, installs desktop launchers, and copies systemd user services.

### 4. Build Moreland with Intel VA-API & Adaptive Bitrate Patch
For Intel Iris Plus / Xe graphics and dynamic bitrate modulation:

```bash
git clone https://github.com/adiimanav/moreland.git ~/Repos/moreland
cd ~/Repos/moreland
git apply /path/to/droid-link/patches/moreland-intel-vaapi-dmabuf.patch
cargo build --release
cp target/release/moreland ~/.local/bin/
```

### 5. Build & Install Android Companion Apps
Droid Link includes first-party, reproducibly buildable Android companion apps under `android/`:
- **Droid Link Display** (`io.github.rayhan099.droidlink.display`): High-framerate H.264 rendering surface with stylus/touch digitization and Picture-in-Picture support.
- **Droid Link Audio** (`io.github.rayhan099.droidlink.audio`): Low-latency TCP PCM audio receiver.

Build both release APKs with a single command:
```bash
./scripts/build-apk.sh
```
Install them to your connected tablet:
```bash
adb install -r android/display/build/outputs/apk/release/display-release.apk
adb install -r android/audio/build/outputs/apk/release/audio-release.apk
```
*(Legacy upstream apps `com.moreland.display` and `com.kaytat.simpleprotocolplayer` remain fully supported via seamless automatic fallback).*

---

## How Audio Routing Works

```
1. Active Default Sink (e.g. C-Media USB Audio)
      ↓ (User runs 'tablet audio')
2. Saved to ~/.config/tablet/<SERIAL>/saved_sink
      ↓
3. Dynamic PipeWire Module Loaded: module-null-sink (Tablet_Audio)
      ↓
4. PipeWire TCP Server: module-simple-protocol-tcp (port 4711, s16le 48000Hz)
      ↓
5. ADB Reverse Forwarding: adb reverse tcp:4711 tcp:4711
      ↓
6. Tablet Player Launched via Intent: io.github.rayhan099.droidlink.audio.action.PLAY
      ↓
7. System Default Sink switched to Tablet_Audio
      ↓
8. Existing audio playback streams migrated to Tablet_Audio
      ↓
9. Disconnect Watchdog background monitor armed
```

When you run `tablet audio stop` or unplug the tablet, DroidLink performs a clean teardown:
- Reads `saved_sink` and restores it as the system default sink.
- Migrates active playback streams back to your laptop speakers.
- Unloads `module-simple-protocol-tcp` and `module-null-sink`.
- Shuts down the Android receiver and cleans up ports.

---

## Testing & Verification

DroidLink separates all verification claims into empirical physical tests, simulated benchmarks, and mock suites:

```bash
# Run CLI & diagnostic verification
./tests/test_cli.sh

# Run multi-device discovery & serial targeting test suite
./tests/test_device_discovery.sh

# Run touch & stylus protocol unit tests
python3 tests/test_input.py

# Run adaptive bitrate controller simulation benchmark
python3 tests/benchmark_adaptive_bitrate.py

# Run isolated PipeWire routing module tests
./tests/test_audio_routing.sh
```

### Empirical Verification Matrix

#### 1. Verified on Physical Hardware (Redmi Pad `24074RPD2I`)
- [x] **Extended Display**: True independent workspace (`1920×1200@90Hz`) on workspace 3 with zero-copy DMA-BUF `AR24` tiled export. Measured **1.6% CPU usage** over 51+ minutes of continuous streaming.
- [x] **USB Disconnect / Reconnect Resilience**: Stream automatically recovers and renegotiates format after physical USB cable drops without crashing the daemon.
- [x] **USB Audio Playback**: 48kHz 16-bit stereo PCM rendered cleanly through tablet quad speakers over ADB reverse port 4711.
- [x] **Watchdog Fail-Safe Restoration**: Automatically switches default audio back to laptop sound card within 4 seconds of tablet disconnection.
- [x] **Stream Independence**: Video, audio, and input streams toggle independently without affecting other subsystems.

#### 2. Mock & Simulation Benchmark Verified
- [x] **Dynamic Adaptive Bitrate**: Benchmark simulated ideal transport (<25ms RTT -> 30 Mbps ceiling), severe congestion (>45ms RTT -> 5 Mbps floor), and noisy 150ms outlier rejection.
- [x] **Wayland Touch & Stylus Protocol**: Verified 24-byte packet serialization, boundary clamping, and uinput slot lifecycle.
- [x] **Multi-Device ADB Discovery**: Verified auto-resolve, ambiguity guard (exit code 2), explicit targeting, and unauthorized device detection.
- [x] **Diagnostics & Waybar**: Verified `tablet doctor` full-stack audit and `tablet-waybar` status JSON.

#### 3. Pending Physical Hardware Validation
- [ ] **Physical Stylus Digitizer**: Stylus pressure and tilt precision on the physical tablet surface is pending sideloading `display-release.apk` (can be completed whenever the user chooses to pause the active display session).

---

## Known Limitations & Architecture Nuances

- **Multitasking & Picture-in-Picture**: Picture-in-Picture mode (`supportsPictureInPicture="true"`, `MediaCodec.setOutputSurface`) allows minimizing the video or switching apps while keeping the stream active. However, Android OS security does *not* permit invisible offscreen video rendering without a hardware display presentation.
- **Multi-Device Concurrency**: DroidLink supports *multi-device discovery and targeting* (driving any specific tablet using `-d <SERIAL>`). Concurrent simultaneous streaming to two tablets at the same time requires dynamic per-device port allocation.

---

## Documentation

- [docs/implementation/RELEASE_READINESS.md](docs/implementation/RELEASE_READINESS.md) — Comprehensive hardware validation and benchmark report.
- [docs/architecture.md](docs/architecture.md) — Detailed technical architecture and pipeline mechanics.
- [docs/installation.md](docs/installation.md) — Step-by-step setup and build guide.
- [docs/troubleshooting.md](docs/troubleshooting.md) — Solutions for common connection, input, and VA-API errors.
- [docs/testing.md](docs/testing.md) — Verification scenarios and test results.
- [android/README.md](android/README.md) — APK building, sideloading, and automation intents.

---

## Roadmap

- [x] Wayland touch and stylus input redirection from Android to Hyprland.
- [x] Direct system tray / Waybar widget for one-click display and audio toggling.
- [x] Robust auto-discovery and serial targeting for multi-tablet setups.
- [x] Dynamic bitrate adjustment based on client ACK round-trip time.
- [x] Picture-in-Picture mode and dynamic surface lifecycle management.
- [x] Full-stack diagnostic and release readiness tool (`tablet doctor`).
- [ ] Direct Wi-Fi / P2P transport mode for wireless operation when USB is disconnected.
- [ ] Reverse audio streaming (using tablet microphone as a Linux input source).
- [ ] Hardware AV1 / HEVC encoding profile support for newer GPUs and tablets.

---

## License & Attribution

- **DroidLink Integration & Scripts**: Released under the [MIT License](LICENSE). Copyright (c) 2026 [Rayhan Khan](https://github.com/Rayhan-099).
- Upstream projects and components are acknowledged in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
