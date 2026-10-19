# DroidLink Verification & Testing Suite

This document records the verification methodology and empirical test results conducted on CachyOS Linux (Kernel 7.2.9) with Hyprland 0.56.2 and a Redmi Pad (model `24074RPD2I`, physical resolution 2560×1600).

---

## Test Scenarios & Evidence

### Test A: Genuine Extended Display
- **Objective**: Verify that the tablet acts as an independent extended display rather than mirroring the primary screen.
- **Commands**:
  ```bash
  tablet
  hyprctl monitors
  ```
- **Observed Result**:
  - `Monitor eDP-1 (ID 0)`: 1920×1080@60Hz at (0, 0)
  - `Monitor moreland (ID 1)`: 1920×1200@90Hz at (1920, 0), `mirrorOf: none`
  - Moved a terminal window across outputs (`hyprctl dispatch movetoworkspacesilent 5`); verified window rendered exclusively on the tablet workspace via `adb exec-out screencap`.
- **Status**: **PASS**

---

### Test B: USB Audio Playback
- **Objective**: Verify that laptop audio is routed exclusively to the tablet speakers over USB.
- **Commands**:
  ```bash
  tablet audio
  pactl get-default-sink
  pw-play --target Tablet_Audio /usr/share/sounds/freedesktop/stereo/alarm-clock-elapsed.oga
  ```
- **Observed Result**:
  - `pactl get-default-sink` switched to `Tablet_Audio`.
  - TCP connection established: `127.0.0.1:4711 <-> 127.0.0.1:47565` via `adb reverse`.
  - Android `dumpsys media.audio_flinger` confirmed:
    `Track 953: Active YES, Client com.kaytat.simpleprotocolplayer (PID 16219), 48000Hz stereo`, with audio frame count incrementing by over 2.8 million frames during playback.
- **Status**: **PASS**

---

### Test C: Manual Audio Restoration
- **Objective**: Verify that stopping tablet audio cleanly restores the prior default laptop audio sink.
- **Commands**:
  ```bash
  tablet audio stop
  pactl get-default-sink
  pactl list short sinks
  ```
- **Observed Result**:
  - Default sink returned immediately to `alsa_output.usb-ASUSTeK_COMPUTER_INC._C-Media_R__Audio-00.analog-stereo`.
  - `Tablet_Audio` virtual null-sink and port 4711 listener were destroyed with zero residue.
  - Moreland extended monitor remained running and undisturbed.
- **Status**: **PASS**

---

### Test D: Disconnection Watchdog Handling
- **Objective**: Verify that physical USB disconnection triggers automatic audio restoration.
- **Commands**:
  ```bash
  tablet status  # confirmed Watchdog monitor RUNNING
  # Device disconnect event
  ```
- **Observed Result**:
  - `tablet-audio-watchdog` detected device absence within 2 consecutive polls (4 seconds).
  - Executed `tablet-audio stop` automatically, logging:
    `Device disconnected. Triggering audio stop & restore...`
  - Restored default laptop audio output without user intervention.
- **Status**: **PASS**

---

### Test E: Display and Audio Independence
- **Objective**: Verify that display and audio functions can be toggled without interfering with each other.
- **Commands**:
  ```bash
  tablet              # Start display only -> Audio remains on laptop
  tablet audio        # Start audio -> Audio routes to tablet, display unaffected
  tablet audio stop   # Stop audio -> Audio returns to laptop, display remains active
  tablet stop         # Stop display -> Everything cleanly terminated
  ```
- **Observed Result**: Zero cross-process interference; video continues streaming uninterrupted during audio starts and stops.
- **Status**: **PASS**

---

### Test F: Reconnection Behavior
- **Objective**: Verify that plugging the tablet back in does not hijack PC audio automatically.
- **Observed Result**: Reconnection leaves audio playing through the laptop speakers. Audio is routed to the tablet only upon explicit invocation of `tablet audio`.
- **Status**: **PASS**
