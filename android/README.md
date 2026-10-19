# Android Companion Applications

DroidLink leverages two Android companion applications over a single wired USB connection:

1. **Moreland Display Client** (`com.moreland.display`): Hardware-accelerated H.264 video decoding surface view.
2. **SimpleProtocolPlayer** (`com.kaytat.simpleprotocolplayer`): Low-latency TCP PCM audio streaming receiver.

---

## 1. SimpleProtocolPlayer (Audio Receiver)

SimpleProtocolPlayer is an open-source Android client originally developed by [kaytat](https://github.com/kaytat/SimpleProtocolPlayer) that receives raw PCM audio streams over a TCP connection and outputs to Android's low-latency `AudioTrack` mixer.

### Building from Source

To build a signed release APK:

```bash
# Clone the repository
git clone https://github.com/kaytat/SimpleProtocolPlayer.git
cd SimpleProtocolPlayer

# Set up Android SDK & Java 17 environment
export ANDROID_HOME="/path/to/android-sdk"
export JAVA_HOME="/path/to/jdk-17"

# Build debug APK
./gradlew assembleDebug

# Output location:
# build/outputs/apk/debug/SimpleProtocolPlayer-debug.apk
```

### Building a Signed Release APK (Optional)

Generate a local signing key:

```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias simpleprotocol -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass YOUR_STORE_PASS -keypass YOUR_KEY_PASS \
  -dname "CN=SimpleProtocol, OU=Audio, O=Personal, C=US"
```

Configure `signingConfigs` in `build.gradle`:

```groovy
android {
    signingConfigs {
        release {
            storeFile file('release.keystore')
            storePassword 'YOUR_STORE_PASS'
            keyAlias 'simpleprotocol'
            keyPassword 'YOUR_KEY_PASS'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled false
        }
    }
}
```

Then build with `./gradlew assembleRelease`.

---

## 2. Moreland Display Client (Video Receiver)

The Moreland Android companion client connects to the host's H.264 stream and displays the desktop with sub-frame latency.

Refer to the official [Moreland repository](https://github.com/adiimanav/moreland) for building instructions:

```bash
git clone https://github.com/adiimanav/moreland.git
cd moreland/android
./gradlew assembleRelease
```

---

## 3. Installation on Xiaomi / Redmi (MIUI & HyperOS)

Xiaomi devices gate `adb install` behind "Install via USB" in Developer Options (which requires a Mi account with SIM card).

If you do not have a Mi account on the tablet:

1. Push the APK to the tablet's storage via ADB:
   ```bash
   adb push path/to/SimpleProtocolPlayer.apk /sdcard/Download/
   ```
2. On your tablet, open the **File Manager** app.
3. Navigate to **Download**.
4. Tap **`SimpleProtocolPlayer.apk`**.
5. When prompted by the MIUI security scanner, allow installation from this source and tap **Install**.

---

## 4. Automation Contract

DroidLink controls SimpleProtocolPlayer completely headless via Android Intent actions:

- **Start Audio Service**:
  ```bash
  adb shell am start-foreground-service \
    -a com.kaytat.simpleprotocolplayer.action.PLAY \
    --es ip_addr "127.0.0.1" \
    --ei audio_port 4711 \
    --ei sample_rate 48000 \
    --ez stereo true \
    --ez retry true \
    --ez use_performance_mode true \
    --ez use_min_buffer true \
    com.kaytat.simpleprotocolplayer/.MusicService
  ```

- **Stop Audio Service**:
  ```bash
  adb shell am start-service \
    -a com.kaytat.simpleprotocolplayer.action.STOP \
    com.kaytat.simpleprotocolplayer/.MusicService
  ```
