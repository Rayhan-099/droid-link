# Droid Link Next Phase: Architecture & System Audit

**Audit Date:** 2026-10-19  
**Platform:** CachyOS Linux (Kernel 7.2.9-2-cachyos, x86_64), Hyprland 0.56.2 (Wayland with Lua config), PipeWire 1.6.9, ADB 35.0.2  
**Target Hardware:** Intel Iris Plus Graphics G1 (Ice Lake), Redmi Pad (24074RPD2I / HyperOS 1.0 / Android 14) connected via USB (serial `f25e01ff`)

---

## 1. Executive Summary & Current Operational Reality

| Subsystem | Live Status | Working Reality & Empirical Findings |
| :--- | :--- | :--- |
| **Virtual Extended Display** | **WORKING** | Hyprland headless monitor `moreland` (1920x1200@90Hz at 1920x0) active with Intel VA-API DMA-BUF (`AR24` tiled modifier). |
| **Tablet Audio Streaming** | **WORKING** | PipeWire `Tablet_Audio` virtual sink + `module-simple-protocol-tcp` on port 4711 streaming 48kHz s16le stereo over ADB reverse tunnel to tablet hardware speakers. |
| **Audio Restoration** | **WORKING** | Previous hardware sink saved to `~/.config/tablet/saved_sink`; watchdog monitor restores laptop audio on disconnect. |
| **Android Applications** | **BUILT & TESTED** | First-party multi-module Gradle project under `android/` (`:display`, `:audio`) produces validly signed release APKs (`io.github.rayhan099.droidlink.*`). |
| **Foreground Video Limitation** | **CONFIRMED** | `SurfaceView` in `DisplayActivity` triggers `surfaceDestroyed` upon app switch; breaks socket connection, stopping host stream. |
| **Wayland Touch & Stylus** | **UNIMPLEMENTED** | Tablet operates strictly as an output display. No touch/stylus coordinate ingestion, protocol transport, or virtual input device exists. |
| **One-Click Controls** | **CLI ONLY** | CLI `tablet` and `tablet-audio` work, but no graphical one-click menu or Waybar custom module is available. |
| **Multi-Tablet Discovery** | **HARDCODED FALLBACK** | Scripts default to `head -n 1` of `adb devices`, risking collision if multiple devices or unauthorized phones are connected. |
| **Dynamic Video Bitrate** | **STATIC ONLY** | Fixed bitrate (20,000 kbps default). No runtime adaptation to USB bus saturation or packet acknowledgement latency. |

---

## 2. Deep-Dive Findings by Subsystem

### 2.1 Display Pipeline & Foreground Limitation
- **Mechanism**: `DisplayActivity` embeds a `SurfaceView`. When backgrounded, Android invokes `surfaceDestroyed(holder)`.
- **Root Cause of Breakage**: `VideoStream.kt` treats surface destruction as an immediate stream error, closing the `LocalSocket`. The host daemon's reader sees EOF, exits the ack loop, and tears down the session.
- **Solution Strategy**:
  1. Separate the streaming connection lifecycle from the transient UI surface lifecycle using an Android Foreground Service (`DisplayService`) or retaining the active decoding loop.
  2. Implement `MediaCodec.setOutputSurface(newSurface)` (API 23+) so that when the user returns or changes orientation, the existing codec instance rebinds to the new surface without socket disruption.
  3. Support Android Picture-in-Picture (`android:supportsPictureInPicture="true"`) allowing the extended monitor to float on top of other Android apps.

### 2.2 Wayland Touch & Stylus Pipeline
- **Host Input Capabilities**: Hyprland 0.56.2 includes first-class support for `Touch` and `Tablets` via Aquamarine/wlroots. When a virtual uinput touch device is created with `name = "DroidLink Touch Screen"`, Hyprland binds it.
- **Coordinate Mapping**: Tablet coordinates $(x_t, y_t) \in [0, W_t] \times [0, H_t]$ must be normalized to $[0.0, 1.0]$ on Android, transmitted over an ADB TCP tunnel, and mapped to the virtual touch device's absolute axis range $[0, 32767]$. Hyprland's `device` block assigns `output = moreland`, guaranteeing clicks stay on the secondary screen.
- **Stylus Capability**: Android `MotionEvent` exposes tool types (`TOOL_TYPE_STYLUS`, `TOOL_TYPE_ERASER`), normalized pressure (`getPressure()`), and tilt.

### 2.3 Desktop Controls & Waybar Integration
- **Desktop Environment**: Hyprland Lua configuration. Desktop includes `fuzzel`, `zenity`, and `notify-send`.
- **Requirements**: Provide a lightweight, non-blocking Waybar custom module script (`bin/droidlink-waybar`) returning JSON (`{"text": "...", "tooltip": "...", "class": "...", "alt": "..."}`) and handling click actions (`tablet`, `tablet stop`, `tablet audio`).
- **Example Configs**: Supply sample Waybar `config` and `style.css` snippets under `examples/waybar/`.

### 2.4 Multi-Device Discovery & Ambiguity Handling
- **Current Defect**: `bin/tablet` uses `devices=$("${ADB}" devices 2>/dev/null | awk '$2 == "device" {print $1}')` and blindly grabs `head -n 1`.
- **Required Behavior**:
  - Accept `--device SERIAL` / `-d SERIAL` or `$DROIDLINK_SERIAL`.
  - Check device authorization state (`device` vs `unauthorized` vs `offline`).
  - If multiple devices are attached and no explicit device is targeted, list available serials with model strings and fail with an unambiguous error code and message.
  - Namespace runtime configs (`~/.config/tablet/<SERIAL>/...`).

### 2.5 Dynamic Bitrate Control
- **Encoder Capabilities**: `vah264enc` (GStreamer VA-API) exposes `bitrate` as a runtime property (`flags: readable, writable, changeable in NULL, READY, PAUSED or PLAYING state`).
- **Telemetry Source**: The daemon's ack loop already measures round-trip time (`sent.elapsed()`) on every frame acknowledgement.
- **Control Strategy**: Implement an adaptive bitrate controller with:
  - Range: $5,000\text{ kbps}$ (min) to $30,000\text{ kbps}$ (max), starting at $20,000\text{ kbps}$.
  - Hysteresis: Step down on sustained round-trip latency $> 45\text{ ms}$; step up gradually after 5 seconds of low latency ($< 25\text{ ms}$).

---

## 3. Physical Device Verification Status

- **Currently Online Device**: Redmi Pad (`24074RPD2I`, serial `f25e01ff`).
- **Live Active Session**: Moreland output `1920x1200@90Hz` running; PipeWire audio stream active.
- **Safety Requirement**: All script and daemon modifications must maintain complete backward compatibility with this running session.
