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
        end

        subgraph VideoPipeline["Video Streaming Pipeline"]
            Capture["Zero-Copy DMA-BUF Capture\n(AR24 / Tiled)"]
            VA["Intel VA-API / vapostproc\n(vah264enc H.264)"]
            MorelandDaemon["Moreland Daemon\n(adb forward)"]
        end

        subgraph AudioPipeline["Audio Streaming Pipeline"]
            AudioApps["Desktop Applications / Media"]
            PipeWire["PipeWire Sound Server"]
            NullSink["Virtual Null-Sink\n(Tablet_Audio)"]
            SimpleTCP["module-simple-protocol-tcp\n(Port 4711, 48kHz Stereo PCM)"]
            Watchdog["tablet-audio-watchdog\n(ADB State Monitor)"]
        end

        Headless --> Capture --> VA --> MorelandDaemon
        AudioApps --> PipeWire --> NullSink --> SimpleTCP
        Watchdog -. Monitors .-> LinuxHost
    end

    subgraph USBTransport["Single Physical USB-C Cable"]
        ADB_Video["ADB Forward Socket\n(localabstract:moreland)"]
        ADB_Audio["ADB Reverse Tunnel\n(tcp:4711 -> tcp:4711)"]
    end

    subgraph AndroidTablet["Android Tablet (Redmi Pad / HyperOS)"]
        DisplayApp["Droid Link Display\n(SurfaceView / MediaCodec)"]
        AudioApp["Droid Link Audio\n(MusicService / AudioTrack)"]
        TabletScreen["Extended Desktop (2560x1600)"]
        TabletSpeakers["Hardware Quad Speakers"]

        DisplayApp --> TabletScreen
        AudioApp --> TabletSpeakers
    end

    MorelandDaemon === ADB_Video === DisplayApp
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

- **State Preservation**: Before routing audio to the tablet, DroidLink saves the exact name of the current active default sink (e.g. `alsa_output.usb-ASUSTeK...analog-stereo`) into `~/.config/tablet/saved_sink`.
- **Active Monitoring**: The `tablet-audio-watchdog` process monitors device connectivity every 2 seconds via `adb devices`.
- **Automatic Recovery**: If the tablet is physically disconnected:
  1. The watchdog immediately executes `tablet-audio stop`.
  2. The default audio sink is switched back to the saved device.
  3. Any active audio streams are migrated back to the laptop sound card.
  4. The PipeWire modules (`Tablet_Audio` and `simple-protocol-tcp`) are unloaded.
  5. The watchdog exits cleanly.
