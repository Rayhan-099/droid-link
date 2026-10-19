#!/usr/bin/env bash
# DroidLink Installer
# Author: Rayhan Khan (https://github.com/Rayhan-099)
# License: MIT

set -euo pipefail

PREFIX="${PREFIX:-${HOME}/.local/bin}"
SYSTEMD_USER_DIR="${HOME}/.config/systemd/user"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "=== Running in DRY-RUN mode. No files will be modified. ==="
fi

echo "=== Installing DroidLink ==="
echo "Target bin directory:     ${PREFIX}"
echo "Target systemd directory: ${SYSTEMD_USER_DIR}"
echo ""

# 1. Dependency checks
echo "Checking dependencies..."
MISSING_DEPS=()
for cmd in pactl adb systemctl ss; do
    if ! command -v "${cmd}" >/dev/null 2>&1 && [[ ! -x "${HOME}/.local/bin/${cmd}" ]]; then
        MISSING_DEPS+=("${cmd}")
    fi
done

if [[ ${#MISSING_DEPS[@]} -gt 0 ]]; then
    echo "Warning: The following recommended dependencies were not found in PATH or ~/.local/bin:" >&2
    for d in "${MISSING_DEPS[@]}"; do
        echo "  - ${d}" >&2
    done
    echo "Please ensure these utilities are installed for full functionality." >&2
else
    echo "All core dependencies verified (pactl, adb, systemctl, ss)."
fi

# 2. Check Moreland binary
if [[ ! -x "${PREFIX}/moreland" && ! -x "$(command -v moreland || true)" ]]; then
    echo "Notice: Moreland daemon binary not found at ${PREFIX}/moreland."
    echo "  See docs/installation.md on how to build Moreland with Intel VA-API patch."
fi

# 3. Create target directories
if [[ "${DRY_RUN}" == false ]]; then
    mkdir -p "${PREFIX}"
    mkdir -p "${SYSTEMD_USER_DIR}"
fi

# 4. Install binaries
for bin in tablet tablet-audio tablet-audio-watchdog; do
    src="${REPO_DIR}/bin/${bin}"
    dst="${PREFIX}/${bin}"
    if [[ -f "${dst}" ]]; then
        echo "Backing up existing ${dst} to ${dst}.bak"
        [[ "${DRY_RUN}" == false ]] && cp -f "${dst}" "${dst}.bak"
    fi
    echo "Installing ${bin} -> ${dst}"
    if [[ "${DRY_RUN}" == false ]]; then
        cp -f "${src}" "${dst}"
        chmod +x "${dst}"
    fi
done

# 5. Install systemd service
service_src="${REPO_DIR}/systemd/moreland.service"
service_dst="${SYSTEMD_USER_DIR}/moreland.service"
if [[ -f "${service_dst}" ]]; then
    echo "Backing up existing ${service_dst} to ${service_dst}.bak"
    [[ "${DRY_RUN}" == false ]] && cp -f "${service_dst}" "${service_dst}.bak"
fi
echo "Installing moreland.service -> ${service_dst}"
if [[ "${DRY_RUN}" == false ]]; then
    cp -f "${service_src}" "${service_dst}"
    systemctl --user daemon-reload || true
fi

echo ""
echo "=== DroidLink installation complete! ==="
echo "Ensure ${PREFIX} is in your PATH."
echo "Run 'tablet status' to check connection status."
