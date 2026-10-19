#!/usr/bin/env bash
# DroidLink — Configure non-root /dev/uinput permissions for Wayland Touch & Stylus
# Author: Rayhan Khan (https://github.com/Rayhan-099)
# License: MIT

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULE_SRC="${SCRIPT_DIR}/99-droidlink-uinput.rules"
RULE_DST="/etc/udev/rules.d/99-droidlink-uinput.rules"

echo "=== DroidLink uinput Permission Setup ==="

if [[ -w "/dev/uinput" ]]; then
    echo "Success: /dev/uinput is already writable by current user ($(whoami))."
    echo "No permission modifications needed."
    exit 0
fi

echo "Current user does not have write access to /dev/uinput."
echo "Installing udev rule to ${RULE_DST}..."

if command -v sudo >/dev/null 2>&1; then
    sudo cp -f "${RULE_SRC}" "${RULE_DST}"
    sudo udevadm control --reload-rules
    sudo udevadm trigger --subsystem-match=misc
    echo ""
    echo "Rule installed successfully. Verifying /dev/uinput..."
    if [[ -w "/dev/uinput" ]]; then
        echo "Success: /dev/uinput is now writable."
    else
        echo "Notice: You may need to log out and log back in (or re-plug USB) for uaccess permissions to take effect."
    fi
else
    echo "Error: sudo not available. Please manually copy:"
    echo "  sudo cp ${RULE_SRC} ${RULE_DST}"
    echo "  sudo udevadm control --reload-rules && sudo udevadm trigger"
    exit 1
fi
