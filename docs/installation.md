# DroidLink Installation Guide

This guide walks through setting up DroidLink on Arch-based Linux distributions (CachyOS, Arch Linux) running Hyprland with an Android tablet over USB.

---

## 1. Prerequisites

### Host System Requirements
- **OS**: CachyOS / Arch Linux (or modern Wayland-based distribution)
- **Compositor**: Hyprland (0.50+)
- **Audio Server**: PipeWire (1.0+) with `pipewire-pulse` and `wireplumber`
- **Video Subsystem**: VA-API drivers (`intel-media-driver` for Intel, `mesa` for AMD)
- **Tools**: `android-tools` (`adb`), `pulseaudio-utils` (`pactl`), `iproute2` (`ss`), `systemd`

Install package prerequisites via `pacman`:

```bash
sudo pacman -S android-tools pulseaudio-utils gstreamer gst-plugins-base gst-plugins-good gst-plugins-bad gst-plugin-va
```

### Android Tablet Requirements
- Android 8.0+ (Tested on Android 14 / Xiaomi HyperOS 1.0)
- **Developer Options** enabled
- **USB Debugging** enabled
- USB-C 3.0 / 3.1 data cable

---

## 2. Automated Installation

Clone the repository and run the installer:

```bash
git clone https://github.com/Rayhan-099/droid-link.git
cd droid-link

# Test dry-run first (optional)
./scripts/install.sh --dry-run

# Install binaries into ~/.local/bin and systemd unit into ~/.config/systemd/user
./scripts/install.sh
```

Ensure `~/.local/bin` is in your `$PATH`. In your shell profile (`~/.bashrc`, `~/.zshrc`, or `~/.config/fish/config.fish`):

```bash
export PATH="$HOME/.local/bin:$PATH"
```

---

## 3. Building Moreland with Intel VA-API Patch

For Intel GPU users (Iris Plus, Xe, Arc), Moreland requires the format negotiation patch included in `patches/`:

```bash
# 1. Clone upstream Moreland
git clone https://github.com/adiimanav/moreland.git ~/Repos/moreland
cd ~/Repos/moreland

# 2. Apply the DroidLink Intel DMA-BUF patch
git apply /path/to/droid-link/patches/moreland-intel-vaapi-dmabuf.patch

# 3. Build release binary
cargo build --release

# 4. Install binary to ~/.local/bin
cp target/release/moreland ~/.local/bin/
```

---

## 4. Android Companion App Setup

Droid Link provides first-party, reproducibly buildable companion apps in the `android/` directory:

1. **Build both companion APKs from source**:
   ```bash
   cd android
   ./gradlew assembleRelease
   ```
2. **Install directly via ADB**:
   ```bash
   adb install -r display/build/outputs/apk/release/display-release.apk
   adb install -r audio/build/outputs/apk/release/audio-release.apk
   ```
3. **Xiaomi / Redmi (MIUI & HyperOS) alternative**:
   If "Install via USB" requires an account login, push to storage and install via File Manager:
   ```bash
   adb push display/build/outputs/apk/release/display-release.apk /sdcard/Download/
   adb push audio/build/outputs/apk/release/audio-release.apk /sdcard/Download/
   ```
   Open **File Manager** on the tablet, navigate to **Downloads**, and tap each APK to install.
   *(Legacy upstream apps `com.moreland.display` and `com.kaytat.simpleprotocolplayer` remain fully supported via automatic fallback).*

---

## 5. Verification

Connect your tablet with a USB cable, unlock it, and run:

```bash
tablet status
```

You should see:
- Display Service status
- Tablet USB Connection: `AUTHORIZED & CONNECTED`
- Tablet Audio Status: `INACTIVE`
