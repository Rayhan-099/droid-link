#!/usr/bin/env bash
# DroidLink Uninstaller
# Author: Rayhan Khan (https://github.com/Rayhan-099)
# License: MIT

set -euo pipefail

PREFIX="${PREFIX:-${HOME}/.local/bin}"
SYSTEMD_USER_DIR="${HOME}/.config/systemd/user"
STATE_DIR="${XDG_CONFIG_HOME:-${HOME}/.config}/tablet"

echo "=== Uninstalling DroidLink ==="

# 1. Stop active services if running
if command -v tablet >/dev/null 2>&1; then
    echo "Stopping any active DroidLink services..."
    tablet stop >/dev/null 2>&1 || true
fi

# 2. Disable and remove systemd service
if systemctl --user is-active moreland.service >/dev/null 2>&1; then
    systemctl --user stop moreland.service 2>/dev/null || true
fi
if systemctl --user is-enabled moreland.service >/dev/null 2>&1; then
    systemctl --user disable moreland.service 2>/dev/null || true
fi

service_dst="${SYSTEMD_USER_DIR}/moreland.service"
if [[ -f "${service_dst}" ]]; then
    echo "Removing ${service_dst}"
    rm -f "${service_dst}"
    if [[ -f "${service_dst}.bak" ]]; then
        echo "Restoring previous backup: ${service_dst}.bak -> ${service_dst}"
        mv "${service_dst}.bak" "${service_dst}"
    fi
    systemctl --user daemon-reload || true
fi

# 3. Remove installed binaries
for bin in tablet tablet-audio tablet-audio-watchdog; do
    dst="${PREFIX}/${bin}"
    if [[ -f "${dst}" ]]; then
        echo "Removing ${dst}"
        rm -f "${dst}"
        if [[ -f "${dst}.bak" ]]; then
            echo "Restoring previous backup: ${dst}.bak -> ${dst}"
            mv "${dst}.bak" "${dst}"
        fi
    fi
done

# 4. Clean runtime state
if [[ -d "${STATE_DIR}" ]]; then
    echo "Cleaning state directory: ${STATE_DIR}"
    rm -rf "${STATE_DIR}"
fi

echo ""
echo "=== DroidLink uninstalled successfully ==="
