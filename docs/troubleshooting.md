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

## 3. Disconnect Watchdog Diagnostics

Check the watchdog activity log:
```bash
cat ~/.config/tablet/watchdog.log
```
To restart the watchdog cleanly:
```bash
tablet audio stop
tablet audio
```
