# DroidLink Troubleshooting Guide

This guide covers common issues and resolutions when running DroidLink.

---

## 1. Video / Display Issues

### Error: `streaming stopped, reason not-negotiated (-4)`
**Symptom**: Moreland logs show `vapostproc0:sink caps ... not accepted` on Intel GPUs.
**Root Cause**: The compositor is exporting `AR24` (or `XR24` with tiled modifiers) while upstream Moreland only requested linear `XR24`.
**Solution**: Ensure you applied the Intel VA-API patch located at `patches/moreland-intel-vaapi-dmabuf.patch` before compiling Moreland.

### Virtual Monitor Doesn't Appear in Hyprland
**Check**:
```bash
hyprctl monitors
```
If only your primary screen is visible:
1. Verify the Moreland service is active: `systemctl --user status moreland.service`.
2. Check journal logs: `journalctl --user -u moreland.service -n 50 --no-pager`.
3. Ensure the Moreland companion app is open in the foreground on the tablet.

---

## 2. Audio Streaming Issues

### Device Shows "Unauthorized" or "Offline"
**Check**:
```bash
adb devices
```
If it shows `unauthorized`:
1. Check the tablet screen for the **"Allow USB debugging?"** prompt.
2. Select **"Always allow from this computer"** and tap **OK**.

### Tablet Audio Plays No Sound
1. Verify PipeWire TCP port is listening:
   ```bash
   ss -tlnp | grep 4711
   ```
2. Verify ADB reverse tunnel is active:
   ```bash
   adb reverse --list
   ```
3. Check if Android `SimpleProtocolPlayer` service is running:
   ```bash
   adb shell dumpsys activity services com.kaytat.simpleprotocolplayer
   ```
4. Check tablet volume settings — make sure media volume is not muted.
5. If running on Xiaomi HyperOS, ensure SimpleProtocolPlayer is not restricted by battery saver:
   - **Settings > Apps > Manage apps > Simple Protocol Player > Battery saver > No restrictions**.

### Audio Didn't Return to Laptop Output After Disconnect
Run manual audio stop:
```bash
tablet audio stop
```
This unloads all `Tablet_Audio` virtual sinks and restores your default sound card immediately.

---

## 3. Touch & Stylus Input Issues

### Error: `Permission denied: '/dev/uinput'`
**Symptom**: `tablet input start` fails with permission errors accessing `/dev/uinput`.
**Solution**:
1. Run the non-root uinput setup script:
   ```bash
   sudo ./scripts/setup-uinput.sh
   ```
2. Log out and log back in, or refresh groups with `newgrp input`.
3. Verify permissions: `ls -l /dev/uinput` should show group `input` with read/write permissions (`crw-rw----`).

### Touch Events Offset or Appearing on Laptop Monitor
**Symptom**: Tapping on the tablet moves the cursor or clicks windows on the primary laptop screen instead of the tablet.
**Root Cause**: Hyprland maps new input devices to the entire virtual desktop by default unless bound to a specific output.
**Solution**:
Add output binding directives to your `~/.config/hypr/hyprland.conf`:
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
Reload Hyprland config: `hyprctl reload`.

---

## 4. Multi-Device & ADB Discovery Issues

### Error: `Multiple connected Android devices detected` (Exit Code 2)
**Symptom**: Running `tablet` or `tablet audio` fails with:
```
[ERROR] Multiple connected Android devices detected (2).
Specify the target tablet with: -d <SERIAL> or --device <SERIAL>
```
**Solution**:
1. Run `tablet devices` to list all connected devices and their serials:
   ```bash
   tablet devices
   ```
2. Run your command with the explicit `-d <SERIAL>` option:
   ```bash
   tablet -d 24074RPD2I start
   tablet -d 24074RPD2I audio
   ```

### Device Shows "unauthorized" (Exit Code 3)
**Symptom**: Tool reports device is unauthorized.
**Solution**:
Unlock the tablet screen and look for the popup dialog: **"Allow USB debugging?"**.
Tick **"Always allow from this computer"** and tap **Allow**.

---

## 5. Disconnect Watchdog Diagnostics

Check the watchdog activity log for the target device:
```bash
cat ~/.config/tablet/<SERIAL>/watchdog.log
# (Or ~/.config/tablet/watchdog.log for legacy single-device setups)
```
To restart the watchdog cleanly:
```bash
tablet audio stop
tablet audio
```

---

## 6. Xiaomi HyperOS / MIUI Nuances

### "Install via USB" Dialog Requires Mi Account
Xiaomi requires a SIM card and active Mi Account to enable "Install via USB" in Developer Options.
**Workaround**: You do not need "Install via USB" to use DroidLink! Simply push the APK to internal storage:
```bash
adb push android/display/build/outputs/apk/release/display-release.apk /sdcard/Download/
adb push android/audio/build/outputs/apk/release/audio-release.apk /sdcard/Download/
```
Open **File Manager** on the tablet, go to **Downloads**, and tap the APKs to install directly.

### Background Audio Drops Out When Screen Locks
HyperOS aggressively suspends background apps to save power.
**Solution**:
1. Open **Settings > Apps > Manage apps > Droid Link Audio**.
2. Tap **Battery saver** and change to **No restrictions**.
3. Enable **Autostart**.
