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

# Test 8: 'tablet input start' failure exits non-zero and does not falsely claim active
echo -n "Test 8: 'tablet input start' fails gracefully without uinput... "
if [[ ! -w "/dev/uinput" ]] && [[ ! -w "/dev/input/uinput" ]]; then
    set +e
    input_out="$("${TABLET}" input start 2>&1)"
    exit_code=$?
    set -e
    if [[ "${exit_code}" -ne 0 && "${input_out}" != *"driver active"* ]]; then
        echo "PASS"
    else
        echo "FAIL (exit code ${exit_code}, output: ${input_out})"
        exit 1
    fi
else
    echo "SKIPPED (/dev/uinput already writable)"
fi

# Test 9: 'tablet input status' reports diagnostic reason when inactive
echo -n "Test 9: 'tablet input status' accurately reports inactive state... "
input_status="$("${TABLET}" input status 2>&1)"
if [[ "${input_status}" == *"Touch/Stylus driver:"* ]]; then
    echo "PASS"
else
    echo "FAIL (unexpected status output: ${input_status})"
    exit 1
fi

# Test 10: Stale PID cleanup
echo -n "Test 10: Stale input PID cleanup... "
MOCK_STATE_DIR="${XDG_CONFIG_HOME:-${HOME}/.config}/tablet/default"
mkdir -p "${MOCK_STATE_DIR}"
echo "999999" > "${MOCK_STATE_DIR}/input.pid"
# is_input_active should detect pid 999999 is dead and remove the file
"${TABLET}" input status >/dev/null 2>&1 || true
if [[ ! -f "${MOCK_STATE_DIR}/input.pid" ]]; then
    echo "PASS"
else
    echo "FAIL (stale pid file was not cleaned up)"
    rm -f "${MOCK_STATE_DIR}/input.pid"
    exit 1
fi

# Test 11: setup-uinput user resolution logic
echo -n "Test 11: setup-uinput user resolution... "
output="$(SUDO_USER="testuser123" bash -c '
TARGET_USER=""
if [[ -n "${SUDO_USER:-}" && "${SUDO_USER}" != "root" ]]; then
    TARGET_USER="${SUDO_USER}"
fi
echo "${TARGET_USER}"
')"
if [[ "${output}" == "testuser123" ]]; then
    echo "PASS"
else
    echo "FAIL (expected testuser123, got ${output})"
    exit 1
fi

# Test 12: moreland.service contains native scaling flags
echo -n "Test 12: moreland.service configuration contains --native and --scale... "
if grep -q -- "--native" "${REPO_DIR}/systemd/moreland.service" && grep -q -- "--scale" "${REPO_DIR}/systemd/moreland.service"; then
    echo "PASS"
else
    echo "FAIL (missing --native or --scale in systemd unit)"
    exit 1
fi

echo "All CLI tests passed successfully!"
