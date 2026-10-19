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

# Install binaries into ~/.local/bin, desktop launchers, and systemd units
./scripts/install.sh
```

Ensure `~/.local/bin` is in your `$PATH`. In your shell profile (`~/.bashrc`, `~/.zshrc`, or `~/.config/fish/config.fish`):

```bash
export PATH="$HOME/.local/bin:$PATH"
```

---

## 3. Configuring uinput for Touch & Stylus (Non-Root)

To allow the `droidlink-input` daemon to inject touch screen and stylus tablet events without running as root:

```bash
# Run the automated uinput setup script
sudo ./scripts/setup-uinput.sh
```

This installs `/etc/udev/rules.d/99-droidlink-uinput.rules`, loads the `uinput` kernel module, adds the `uaccess` tag, and adds your user to the `input` group.

To map input to the extended tablet display in Hyprland, ensure your `~/.config/hypr/hyprland.conf` contains:

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

## 4. Building Moreland with Intel VA-API & Adaptive Bitrate Patch

For Intel GPU users (Iris Plus, Xe, Arc) and dynamic bitrate modulation:

```bash
# 1. Clone upstream Moreland
git clone https://github.com/adiimanav/moreland.git ~/Repos/moreland
cd ~/Repos/moreland

# 2. Apply the DroidLink Intel DMA-BUF & Adaptive Bitrate patch
git apply /path/to/droid-link/patches/moreland-intel-vaapi-dmabuf.patch

# 3. Build release binary
cargo build --release

# 4. Install binary to ~/.local/bin
cp target/release/moreland ~/.local/bin/
```

---

## 5. Android Companion App Setup

Droid Link provides first-party, reproducibly buildable companion apps in the `android/` directory:

1. **Build both companion APKs from source**:
   Use the automated build helper (auto-resolves JDK 17 and Android SDK):
   ```bash
   ./scripts/build-apk.sh
   ```
2. **Install directly via ADB**:
   ```bash
   adb install -r android/display/build/outputs/apk/release/display-release.apk
   adb install -r android/audio/build/outputs/apk/release/audio-release.apk
   ```

### Xiaomi HyperOS & MIUI Specific Instructions

Xiaomi devices have additional security safeguards that may affect ADB and background services:

1. **Enabling Developer Options**:
   - Go to **Settings > About tablet > Detailed info and specs**.
   - Tap **OS version** 7 times until you see *"You are now a developer!"*.
2. **Enabling USB Debugging**:
   - Go to **Settings > Additional settings > Developer options**.
   - Turn on **USB debugging**.
   - When connecting the cable to Linux, accept the **"Allow USB debugging?"** prompt and check **"Always allow from this computer"**.
3. **Bypassing "Install via USB" Account Lock**:
   - If MIUI / HyperOS prevents direct `adb install` because a SIM card or Mi Account is not logged in, sideload the APKs directly via storage:
     ```bash
     adb push display/build/outputs/apk/release/display-release.apk /sdcard/Download/
     adb push audio/build/outputs/apk/release/audio-release.apk /sdcard/Download/
     ```
   - Open the tablet's **File Manager**, tap **Downloads**, and tap each APK to install.
4. **Preventing Background Audio Sleep**:
   - To prevent HyperOS aggressive RAM management from killing background audio:
   - Go to **Settings > Apps > Manage apps > Droid Link Audio > Battery saver**.
   - Select **No restrictions**.
   - Enable **Autostart** if you want seamless playback.

*(Legacy upstream apps `com.moreland.display` and `com.kaytat.simpleprotocolplayer` remain fully supported via automatic fallback).*

---

## 6. Waybar Status Bar Integration (Optional)

Add one-click toggle controls directly to your Waybar panel:

1. Add the custom module to `~/.config/waybar/config.jsonc`:
   ```jsonc
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
2. Style the widget using `examples/waybar/style.css`.

---

## 7. Verification

Connect your tablet with a USB cable, unlock it, and run:

```bash
# Verify connected devices
tablet devices

# Check live connection and stream status
tablet status
```

You should see:
- Display Service status
- Tablet USB Connection: `AUTHORIZED & CONNECTED`
- Tablet Audio Status: `INACTIVE` (or `ACTIVE`)
- Connected device serial and model info
