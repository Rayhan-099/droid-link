#!/usr/bin/env bash
# DroidLink CLI Automated Test Suite
# Tests CLI options, syntax, Waybar module output, and safe query paths

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TABLET="${REPO_DIR}/bin/tablet"
WAYBAR="${REPO_DIR}/bin/tablet-waybar"

echo "=== Running DroidLink CLI Tests ==="

# Test 1: Syntax check with bash -n & python -m py_compile
echo -n "Test 1: Shell & Python syntax verification... "
bash -n "${REPO_DIR}/bin/tablet"
bash -n "${REPO_DIR}/bin/tablet-audio"
bash -n "${REPO_DIR}/bin/tablet-audio-watchdog"
bash -n "${REPO_DIR}/bin/droidlink-device"
bash -n "${REPO_DIR}/bin/tablet-waybar"
bash -n "${REPO_DIR}/scripts/install.sh"
bash -n "${REPO_DIR}/scripts/uninstall.sh"
bash -n "${REPO_DIR}/scripts/setup-uinput.sh"
python3 -m py_compile "${REPO_DIR}/bin/droidlink-input"
echo "PASS"

# Test 2: Invalid argument handling
echo -n "Test 2: Invalid argument returns non-zero... "
if "${TABLET}" invalid_cmd 2>/dev/null; then
    echo "FAIL (expected non-zero exit)"
    exit 1
fi
echo "PASS"

# Test 3: Status command works non-destructively
echo -n "Test 3: 'tablet status' runs cleanly... "
"${TABLET}" status >/dev/null 2>&1
echo "PASS"

# Test 4: Devices command lists connected devices
echo -n "Test 4: 'tablet devices' runs cleanly... "
"${TABLET}" devices >/dev/null 2>&1
echo "PASS"

# Test 5: Waybar custom module returns valid JSON
echo -n "Test 5: 'tablet-waybar status' returns valid JSON... "
waybar_json="$("${WAYBAR}" status)"
echo "${waybar_json}" | python3 -m json.tool >/dev/null 2>&1
echo "PASS"

# Test 6: Installer dry-run
echo -n "Test 6: Installer --dry-run completes cleanly... "
"${REPO_DIR}/scripts/install.sh" --dry-run >/dev/null 2>&1
echo "PASS"

# Test 7: Doctor diagnostic command runs cleanly
echo -n "Test 7: 'tablet doctor' produces diagnostic report... "
"${TABLET}" doctor >/dev/null 2>&1
echo "PASS"

echo "All CLI tests passed successfully!"
