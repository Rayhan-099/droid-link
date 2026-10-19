#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# DroidLink — Reproducible Android Companion App Build Script
# Author: Rayhan Khan (https://github.com/Rayhan-099)

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${REPO_DIR}/android"

echo "=== DroidLink Android App Build ==="

# 1. Resolve JDK 17+
if [[ -z "${JAVA_HOME:-}" ]]; then
    CANDIDATES=(
        "${HOME}/.local/share/jdk-17"
        "/usr/lib/jvm/java-17-openjdk"
        "/usr/lib/jvm/java-21-openjdk"
        "/usr/lib/jvm/default"
    )
    for c in "${CANDIDATES[@]}"; do
        if [[ -x "${c}/bin/javac" ]]; then
            export JAVA_HOME="${c}"
            break
        fi
    done
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
    echo "Using JAVA_HOME: ${JAVA_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
else
    echo "Warning: JAVA_HOME not set. Falling back to default system java."
fi

# 2. Resolve Android SDK
if [[ -z "${ANDROID_HOME:-}" ]]; then
    SDK_CANDIDATES=(
        "${HOME}/.local/share/android-sdk"
        "${HOME}/Android/Sdk"
        "/opt/android-sdk"
    )
    for s in "${SDK_CANDIDATES[@]}"; do
        if [[ -d "${s}" ]]; then
            export ANDROID_HOME="${s}"
            break
        fi
    done
fi

if [[ -n "${ANDROID_HOME:-}" ]]; then
    echo "Using ANDROID_HOME: ${ANDROID_HOME}"
else
    echo "Error: Android SDK not found. Set ANDROID_HOME or install to ~/.local/share/android-sdk" >&2
    exit 1
fi

# 3. Build Release APKs
echo ""
echo "Building :display and :audio modules..."
cd "${ANDROID_DIR}"
./gradlew assembleRelease

DISPLAY_APK="${ANDROID_DIR}/display/build/outputs/apk/release/display-release.apk"
AUDIO_APK="${ANDROID_DIR}/audio/build/outputs/apk/release/audio-release.apk"

echo ""
echo "=== Build Complete ==="
if [[ -f "${DISPLAY_APK}" && -f "${AUDIO_APK}" ]]; then
    echo "Generated APKs:"
    echo "  [✓] Display: ${DISPLAY_APK} ($(du -h "${DISPLAY_APK}" | cut -f1))"
    echo "  [✓] Audio:   ${AUDIO_APK} ($(du -h "${AUDIO_APK}" | cut -f1))"
    echo ""
    echo "To install on connected tablet:"
    echo "  adb install -r ${DISPLAY_APK}"
    echo "  adb install -r ${AUDIO_APK}"
else
    echo "Error: One or more APK outputs were not generated." >&2
    exit 1
fi
