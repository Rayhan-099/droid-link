# DroidLink Architecture & Technical Design

DroidLink integrates an Android tablet into a Linux desktop environment as an independent extended monitor and dedicated audio endpoint.

---

## Architecture Overview

```mermaid
flowchart TB
    subgraph LinuxHost["Linux Host (CachyOS / Hyprland)"]
        direction TB
        subgraph Compositor["Hyprland (Wayland)"]
            eDP["Primary Display (eDP-1)"]
            Headless["Headless Virtual Output (moreland)"]
            HyprInput["Input Engine (Mapped to moreland)"]
        end

        subgraph VideoPipeline["Video Streaming Pipeline"]
            Capture["Zero-Copy DMA-BUF Capture\n(AR24 / Tiled)"]
            VA["Intel VA-API / vapostproc\n(vah264enc H.264)"]
            MorelandDaemon["Moreland Daemon\n(Adaptive Bitrate Controller)"]
        end

        subgraph AudioPipeline["Audio Streaming Pipeline"]
            AudioApps["Desktop Applications / Media"]
            PipeWire["PipeWire Sound Server"]
            NullSink["Virtual Null-Sink\n(Tablet_Audio)"]
            SimpleTCP["module-simple-protocol-tcp\n(Port 4711, 48kHz Stereo PCM)"]
            Watchdog["tablet-audio-watchdog\n(ADB State Monitor)"]
        end

        subgraph InputPipeline["Input Pipeline"]
            UInput["droidlink-input Daemon\n(/dev/uinput)"]
            UInput --> HyprInput
        end

        Headless --> Capture --> VA --> MorelandDaemon
        AudioApps --> PipeWire --> NullSink --> SimpleTCP
        Watchdog -. Monitors .-> LinuxHost
    end

    subgraph USBTransport["Single Physical USB-C Cable"]
        ADB_Video["ADB Forward Socket\n(localabstract:moreland)"]
        ADB_Audio["ADB Reverse Tunnel\n(tcp:4711 -> tcp:4711)"]
        ADB_Input["ADB Reverse Tunnel\n(tcp:4820 -> tcp:4820)"]
    end

    subgraph AndroidTablet["Android Tablet (Redmi Pad / HyperOS)"]
        DisplayApp["Droid Link Display\n(SurfaceView / MediaCodec / PiP)"]
        TouchClient["TouchClient\n(Digitizer Streamer)"]
        AudioApp["Droid Link Audio\n(MusicService / AudioTrack)"]
        TabletScreen["Extended Desktop (2560x1600)"]
        TabletSpeakers["Hardware Quad Speakers"]

        DisplayApp --> TabletScreen
        DisplayApp --> TouchClient
        AudioApp --> Speakers
    end

    MorelandDaemon === ADB_Video === DisplayApp
    TouchClient === ADB_Input === UInput
    SimpleTCP === ADB_Audio === AudioApp
```

---

## 1. Video Pipeline: Zero-Copy VA-API DMA-BUF

1. **Virtual Display Creation**: The Moreland daemon communicates with Hyprland via `hyprctl output create headless moreland`, establishing a dedicated output (`1920x1200@90Hz`).
2. **Wayland Buffer Export**: Hyprland exports framebuffers as Linux DMA-BUF file descriptors.
3. **Format & Modifier Negotiation**:
   - Upstream Moreland was designed around AMD VCN and hardcoded `capture::XR24` with linear modifier `0x0`.
   - On Intel Iris Plus / Xe GPUs, the Intel Media Driver (`iHD`) rejects linear `XR24` on `vapostproc`'s DMA-BUF sink pad.
   - DroidLink includes an upstream patch that queries `vapostproc`'s sink pad templates for all supported fourccs and modifier formats, successfully negotiating `AR24` with `I915_FORMAT_MOD_Y_TILED` (`0x0100000000000002`).
4. **Hardware Encoding**: The frame is converted in-GPU to NV12 via `vapostproc` and encoded directly to H.264 using `vah264enc`.
5. **Transport**: Transmitted via an ADB forward tunnel to a `localabstract` UNIX domain socket on Android.

---

## 2. Audio Pipeline: Low-Latency PipeWire TCP

1. **Virtual Audio Sink**: When audio streaming is enabled (`tablet audio`), DroidLink creates an isolated PipeWire virtual sink:
   ```bash
   pactl load-module module-null-sink sink_name=Tablet_Audio sink_properties=device.description="Redmi_Tablet_Audio"
   ```
2. **TCP Streaming Server**: DroidLink hooks PipeWire's `module-simple-protocol-tcp` directly onto `Tablet_Audio.monitor`:
   ```bash
   pactl load-module module-simple-protocol-tcp source=Tablet_Audio.monitor rate=48000 format=s16le channels=2 record=true port=4711
   ```
3. **USB Port Reverse**: ADB maps Android's loopback port `4711` to the host:
   ```bash
   adb reverse tcp:4711 tcp:4711
   ```
4. **Android Audio Mixer**: Droid Link Audio's `MusicService` reads raw PCM from `127.0.0.1:4711` and writes to Android's native `AudioTrack` mixer in low-latency performance mode.

---

## 3. Disconnect Watchdog & Fail-Safe Audio Restoration

To prevent audio dropouts or lost sounds when the tablet is unplugged:

- **State Preservation**: Before routing audio to the tablet, DroidLink saves the exact name of the current active default sink (e.g. `alsa_output.usb-ASUSTeK...analog-stereo`) into `~/.config/tablet/<SERIAL>/saved_sink`.
- **Active Monitoring**: The `tablet-audio-watchdog` process monitors device connectivity every 2 seconds via `adb devices`.
- **Automatic Recovery**: If the tablet is physically disconnected:
  1. The watchdog immediately executes `tablet-audio stop`.
  2. The default audio sink is switched back to the saved device.
  3. Any active audio streams are migrated back to the laptop sound card.
  4. The PipeWire modules (`Tablet_Audio` and `simple-protocol-tcp`) are unloaded.
  5. The watchdog exits cleanly.

---

## 4. Touch & Stylus Pipeline: Wayland Input Injection

To enable the tablet to serve as an interactive input device or drawing surface:

1. **Android Digitizer Capture**:
   - `DisplayActivity.kt` intercepts touch and stylus events via `onTouchEvent(MotionEvent)`.
   - `TouchClient.kt` packs digitizer events into a compact 24-byte binary structure:
     ```
     [Magic: 2B (0x444C)] [Version: 1B] [Action: 1B]
     [ToolType: 1B (FINGER/STYLUS/ERASER)] [PointerId: 1B] [Flags: 2B]
     [NormX: 2B (0..65535)] [NormY: 2B (0..65535)]
     [Pressure: 2B (0..65535)] [TiltX: 2B (-90..90)] [TiltY: 2B (-90..90)]
     [Buttons: 2B (Primary/Secondary/Tertiary)] [Timestamp: 6B]
     ```
2. **Reverse Tunnel**: ADB forwards Android's connection back to host port 4820 (`adb reverse tcp:4820 tcp:4820`).
3. **Host uinput Injection**:
   - `bin/droidlink-input` (pure Python standard library) listens on `127.0.0.1:4820`.
   - Creates two dedicated Linux virtual input devices via `/dev/uinput`:
     - `DroidLink Touch Screen`: Native multi-touch slot device (`ABS_MT_SLOT`, `ABS_MT_TRACKING_ID`, `ABS_MT_POSITION_X/Y`).
     - `DroidLink Stylus`: Absolute digitizer tablet with pressure (`ABS_PRESSURE`), tilt (`ABS_TILT_X/Y`), and barrel button support (`BTN_TOOL_PEN`, `BTN_STYLUS`, `BTN_STYLUS2`).
   - Translates incoming normalized coordinates to native output pixel resolution.
   - Cleans up contacts automatically upon client disconnection to eliminate "ghost/stuck" touches.
4. **Hyprland Output Mapping**:
   Hyprland automatically maps the device coordinates to the tablet screen workspace:
   ```ini
   device {
       name = droidlink-touch-screen
       output = moreland
   }
   device {
       name = droidlink-stylus
       output = moreland
   }
   ```

---

## 5. Dynamic Adaptive Video Bitrate Engine

Network and USB bus congestion can cause frame drops and latency spikes. DroidLink implements dynamic bitrate scaling without interrupting video streaming:

1. **Client ACK RTT Tracking**:
   - Android sends frame acknowledgement packets after decoding each video frame.
   - The Moreland daemon tracks the round-trip latency (`rtt_ms`) of acknowledged frames and computes a 10-sample sliding median.
2. **Bandwidth Adaptation**:
   - **Degradation**: If median RTT exceeds 60ms, the target bitrate is throttled down by 25% (down to a floor of 5,000 kbps).
   - **Recovery**: If median RTT remains below 25ms and consecutive good samples are observed, bitrate increases by 15% (up to a ceiling of 30,000 kbps).
   - **Cooldown**: A 5-second stabilization cooldown prevents rapid oscillations.
3. **GStreamer Dynamic Modulation**:
   - The GStreamer `vah264enc` element explicitly marks its `bitrate` property with `G_PARAM_WRITABLE` in `PLAYING` state.
   - `Encoder::set_bitrate(kbps)` updates the property in-place on the running pipeline without pipeline restarts or visual artifacts.

---

## 6. Multi-Device USB Topology & State Isolation

DroidLink cleanly supports multi-device setups:

1. **Deterministic Discovery**:
   - `bin/droidlink-device` parses `adb devices -l`.
   - Single device: Auto-resolves serial without user friction.
   - Multiple devices: Fails safely with exit code 2 and lists all connected devices unless explicit `-d / --device <SERIAL>` is provided.
   - Unauthorized / Offline devices: Exits with code 3 and guides the user to accept the USB debugging prompt on the tablet.
2. **State Namespacing**:
   - Runtime states (PIDs, saved sinks, reverse ports) are isolated per device serial under `~/.config/tablet/<SERIAL>/`.
   - Prevents conflicting locks or state corruption when multiple Android devices are plugged into the host.

---

## 7. Android Surface Lifecycle & Picture-in-Picture

To resolve the limitation where leaving the app severed the extended display:

1. **Dynamic Surface Re-binding**:
   - Android's `DisplayActivity` uses `MediaCodec.setOutputSurface(newSurface)`.
   - When the user rotates the device or enters/exits Picture-in-Picture, the existing codec remains alive and immediately switches output to the new surface.
2. **Background Frame Sinks**:
   - When the surface is destroyed (e.g. tablet screen lock or app switch), `VideoStream.kt` continues to drain incoming frames from the network socket and acknowledges them without rendering (`releaseOutputBuffer(outIdx, false)`).
   - Prevents host TCP socket buffers from overflowing and tearing down the Moreland daemon.
3. **Picture-in-Picture Support**:
   - `AndroidManifest.xml` configures `supportsPictureInPicture="true"` and `resizeableActivity="true"`.
   - Tapping Home or switching apps transitions DroidLink Display into a floating PiP window.
