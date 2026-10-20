#!/usr/bin/env bash
# DroidLink — Configure non-root /dev/uinput permissions for Wayland Touch & Stylus
# Author: Rayhan Khan (https://github.com/Rayhan-099)
# License: MIT

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULE_SRC="${SCRIPT_DIR}/99-droidlink-uinput.rules"
RULE_DST="/etc/udev/rules.d/99-droidlink-uinput.rules"

echo "=== DroidLink uinput Permission Setup ==="

# 1. Determine target non-root desktop user
TARGET_USER=""
if [[ -n "${SUDO_USER:-}" && "${SUDO_USER}" != "root" ]]; then
    TARGET_USER="${SUDO_USER}"
elif [[ -n "${LOGNAME:-}" && "${LOGNAME}" != "root" ]]; then
    TARGET_USER="${LOGNAME}"
elif [[ -n "${USER:-}" && "${USER}" != "root" ]]; then
    TARGET_USER="${USER}"
else
    # Query logind for active graphical session on seat0
    TARGET_USER="$(loginctl list-sessions --no-legend 2>/dev/null | awk '$3 != "root" {print $3}' | head -n 1 || true)"
fi

if [[ -z "${TARGET_USER}" || "${TARGET_USER}" == "root" ]]; then
    echo "Error: Could not determine non-root desktop user. Please specify TARGET_USER=<username>." >&2
    exit 1
fi

if ! id "${TARGET_USER}" >/dev/null 2>&1; then
    echo "Error: Target user '${TARGET_USER}' does not exist on this system." >&2
    exit 1
fi

# Helper to test write permissions as the target user
is_writable_by_target() {
    local target="$1"
    local path="$2"
    if [[ ! -e "${path}" ]]; then
        return 1
    fi
    if [[ "$(id -u)" -eq 0 ]]; then
        if command -v runuser >/dev/null 2>&1; then
            runuser -u "${target}" -- test -w "${path}" 2>/dev/null
        else
            su -s /bin/sh -c "test -w '${path}'" "${target}" 2>/dev/null
        fi
    else
        test -w "${path}"
    fi
}

echo "Target desktop user: ${TARGET_USER}"

# 2. Check if target user already has write access
if is_writable_by_target "${TARGET_USER}" "/dev/uinput"; then
    echo "Success: /dev/uinput is already writable by user '${TARGET_USER}'."
    echo "No permission modifications needed."
    exit 0
fi

echo "User '${TARGET_USER}' does not have write access to /dev/uinput."
echo "Configuring uinput permissions and udev rule..."

SUDO_CMD=""
if [[ "$(id -u)" -ne 0 ]]; then
    if command -v sudo >/dev/null 2>&1; then
        SUDO_CMD="sudo"
    else
        echo "Error: Root privileges required. Please run this script with sudo." >&2
        exit 1
    fi
fi

# 3. Load uinput module and persist across reboots
echo "Loading uinput kernel module..."
${SUDO_CMD} modprobe uinput 2>/dev/null || true
echo "uinput" | ${SUDO_CMD} tee /etc/modules-load.d/uinput.conf >/dev/null

# 4. Install udev rule
echo "Installing udev rule to ${RULE_DST}..."
${SUDO_CMD} cp -f "${RULE_SRC}" "${RULE_DST}"
${SUDO_CMD} udevadm control --reload-rules
${SUDO_CMD} udevadm trigger --sysname-match=uinput 2>/dev/null || true
${SUDO_CMD} udevadm trigger --subsystem-match=misc 2>/dev/null || true

# 5. Apply session ACL directly for immediate non-root access
if [[ -e "/dev/uinput" ]]; then
    ${SUDO_CMD} setfacl -m "u:${TARGET_USER}:rw" /dev/uinput 2>/dev/null || true
fi

echo ""
echo "Verifying /dev/uinput write permissions for '${TARGET_USER}'..."
if is_writable_by_target "${TARGET_USER}" "/dev/uinput"; then
    echo "Success: /dev/uinput is now writable by '${TARGET_USER}'."
else
    echo "Error: /dev/uinput is still not writable by '${TARGET_USER}'." >&2
    echo "Please ensure user '${TARGET_USER}' has an active graphical seat or is in group 'input'." >&2
    exit 1
fi
