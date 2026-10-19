# Droid Link Next Phase: Implementation Plan & Acceptance Criteria

**Plan Date:** 2026-10-19  
**Execution Order:** Phase B through Phase G

---

## Phase B: Lifecycle & Foreground Video Limitation Reduction

### Objectives
1. Eliminate socket teardown upon Android `surfaceDestroyed` event.
2. Enable dynamic surface reattachment via `MediaCodec.setOutputSurface()`.
3. Add Android Picture-in-Picture (PiP) support to `DisplayActivity`.
4. Ensure clean recovery upon app resumption, rotation, and reconnection.

### Implementation Tasks
- **B.1 (`android/display/src/main/AndroidManifest.xml`)**:
  - Add `android:supportsPictureInPicture="true"` and `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`.
- **B.2 (`android/display/src/main/java/io/github/rayhan099/droidlink/display/VideoStream.kt`)**:
  - Refactor `surfaceChanged` handling: when `currentSurface` becomes null, do NOT drop socket or destroy codec. Instead, discard rendered frames using `releaseOutputBuffer(outIdx, false)`.
  - When a new `Surface` arrives, invoke `codec.setOutputSurface(newSurface)` seamlessly.
  - Implement connection state reporting to UI (`STATE_CONNECTING`, `STATE_CONNECTED`, `STATE_DISCONNECTED`, `STATE_SURFACE_DETACHED`).
- **B.3 (`android/display/src/main/java/io/github/rayhan099/droidlink/display/DisplayActivity.kt`)**:
  - Add `onUserLeaveHint()` to enter Picture-in-Picture mode automatically if supported.
  - Implement informative status overlays with recovery prompts.

---

## Phase C: Multi-Device Discovery & Desktop / Waybar One-Click Controls

### Objectives
1. Eliminate silent fallback to the first device in `adb devices`.
2. Support explicit device selection `--device <SERIAL>` across CLI scripts.
3. Provide a lightweight, non-blocking Waybar custom module returning structured JSON.
4. Supply example Waybar configuration files and documentation.

### Implementation Tasks
- **C.1 (`bin/droidlink-device`)**:
  - Dedicated device discovery library sourced by `tablet`, `tablet-audio`, and `tablet-waybar`.
  - Parses `adb devices -l` extracting serials, models, and states.
  - Returns target serial if valid; fails cleanly with exit code 2 and full device list if ambiguous.
- **C.2 (`bin/tablet` & `bin/tablet-audio`)**:
  - Add `--device <SERIAL>` / `-d <SERIAL>` argument parsing.
  - Namespace state files by serial (`~/.config/tablet/<SERIAL>/...`).
- **C.3 (`bin/tablet-waybar`)**:
  - Non-blocking status reporter producing JSON for Waybar:
    ```json
    {"text": " ", "tooltip": "Droid Link: Display ACTIVE (1920x1200), Audio ACTIVE (Tablet_Audio)", "class": "active"}
    ```
- **C.4 (`examples/waybar/`)**:
  - Add `config.jsonc` snippet and `style.css` styling.

---

## Phase D: Wayland Touch & Stylus Input Pipeline

### Objectives
1. Capture multi-touch and stylus events on Android `DisplayActivity`.
2. Stream normalized events over ADB reverse tunnel (`tcp:4820`).
3. Create a zero-dependency Linux `uinput` virtual device (`DroidLink Touch Screen` & `DroidLink Stylus`).
4. Map touch coordinates 1:1 to Hyprland's `moreland` output.

### Implementation Tasks
- **D.1 (`android/display/.../TouchClient.kt`)**:
  - Background networking thread communicating over TCP `127.0.0.1:4820`.
  - Handles `MotionEvent` with single/multi-touch tracking, tool types (`FINGER`, `STYLUS`), normalized $(X, Y)$, pressure, and action codes.
- **D.2 (`bin/droidlink-input`)**:
  - Linux daemon written in standard Python using `ctypes`/`struct` ioctl interface to `/dev/uinput`.
  - Emits Linux input events: `EV_ABS` (`ABS_MT_SLOT`, `ABS_MT_TRACKING_ID`, `ABS_MT_POSITION_X`, `ABS_MT_POSITION_Y`, `ABS_PRESSURE`), `EV_KEY` (`BTN_TOUCH`, `BTN_TOOL_FINGER`, `BTN_TOOL_PEN`).
  - Handles disconnects with automatic touch cancellation (preventing stuck contacts).
- **D.3 Hyprland Integration & Permissions**:
  - Document Hyprland `device` block mapping virtual touch to `output = moreland`.
  - Provide udev rule template `scripts/99-droidlink-uinput.rules` for non-root access.

---

## Phase E: Dynamic Video Bitrate Control

### Objectives
1. Add runtime bitrate adjustment to Moreland encoder using GStreamer's dynamic `bitrate` property on `vah264enc`.
2. Implement an adaptive bitrate controller driven by frame round-trip acknowledgement latency.
3. Apply hysteresis to prevent oscillations.

### Implementation Tasks
- **E.1 (`crates/encoder/src/lib.rs`)**:
  - Retain `encoder_element: gst::Element` handle.
  - Implement `pub fn set_bitrate(&self, bitrate_kbps: u32)`.
- **E.2 (`crates/daemon/src/session.rs`)**:
  - Implement `BitrateController` with min (5 Mbps), max (30 Mbps), cooldown (5s), and latency threshold (45ms).
  - Update encoder bitrate dynamically in the stream loop.
  - Export patch to `patches/moreland-intel-vaapi-dmabuf.patch`.

---

## Phase F: HyperOS Documentation & Comprehensive Guides

### Objectives
1. Accurately explain HyperOS and MIUI developer option nuances.
2. Update `README.md`, `docs/architecture.md`, and `docs/installation.md` with new features and explicit roadmap statuses.

---

## Phase G: Testing, Validation, Git Commit & Publication

### Objectives
1. Run automated CLI, audio, and input test suites.
2. Build Android release APKs with Gradle.
3. Verify git diff for cleanliness and secrets.
4. Commit and push to `https://github.com/Rayhan-099/droid-link.git`.
