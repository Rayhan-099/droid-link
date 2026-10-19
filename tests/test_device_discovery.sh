#!/usr/bin/env bash
# DroidLink Multi-Device Discovery Automated Test Suite
# Tests device parsing, ambiguity handling, unauthorized states, and explicit selection

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PATH="${REPO_DIR}/bin:${PATH}"

MOCK_BIN_DIR="$(mktemp -d)"
trap 'rm -rf "${MOCK_BIN_DIR}"' EXIT

# Test 1: Single device auto-detects cleanly
cat << 'EOF' > "${MOCK_BIN_DIR}/adb"
#!/usr/bin/env bash
if [[ "$1" == "devices" ]]; then
    echo "List of devices attached"
    echo "device_single_123     device usb:1-1 product:pad model:TestPad device:pad transport_id:1"
fi
EOF
chmod +x "${MOCK_BIN_DIR}/adb"

echo "=== Running Multi-Device Discovery Tests ==="
echo -n "Test 1: Single authorized device auto-resolves... "
ADB="${MOCK_BIN_DIR}/adb"
output="$(ADB="${MOCK_BIN_DIR}/adb" bash -c "source '${REPO_DIR}/bin/droidlink-device' && droidlink_resolve_device")"
if [[ "${output}" == "device_single_123" ]]; then
    echo "PASS"
else
    echo "FAIL (Expected 'device_single_123', got '${output}')"
    exit 1
fi

# Test 2: Multiple authorized devices fails with exit code 2 when none specified
cat << 'EOF' > "${MOCK_BIN_DIR}/adb"
#!/usr/bin/env bash
if [[ "$1" == "devices" ]]; then
    echo "List of devices attached"
    echo "device_alpha_111     device usb:1-1 product:pad1 model:PadAlpha device:pad1 transport_id:1"
    echo "device_beta_222      device usb:1-2 product:pad2 model:PadBeta device:pad2 transport_id:2"
fi
EOF

echo -n "Test 2: Multiple devices without target exits with code 2... "
set +e
ADB="${MOCK_BIN_DIR}/adb" err_msg="$(ADB="${MOCK_BIN_DIR}/adb" bash -c "source '${REPO_DIR}/bin/droidlink-device' && droidlink_resolve_device" 2>&1)"
exit_code=$?
set -e
if [[ "${exit_code}" -eq 2 && "${err_msg}" == *"Multiple authorized Android devices detected"* ]]; then
    echo "PASS"
else
    echo "FAIL (Expected exit code 2 and error message, got exit code ${exit_code})"
    exit 1
fi

# Test 3: Multiple authorized devices with explicit --device succeeds
echo -n "Test 3: Multiple devices with explicit target succeeds... "
output="$(ADB="${MOCK_BIN_DIR}/adb" bash -c "source '${REPO_DIR}/bin/droidlink-device' && droidlink_resolve_device 'device_beta_222'")"
if [[ "${output}" == "device_beta_222" ]]; then
    echo "PASS"
else
    echo "FAIL (Expected 'device_beta_222', got '${output}')"
    exit 1
fi

# Test 4: Unauthorized device reports clear instructions (exit code 3)
cat << 'EOF' > "${MOCK_BIN_DIR}/adb"
#!/usr/bin/env bash
if [[ "$1" == "devices" ]]; then
    echo "List of devices attached"
    echo "unauth_999     unauthorized usb:1-1 transport_id:1"
fi
EOF

echo -n "Test 4: Unauthorized device exits with code 3 and prompt instructions... "
set +e
err_msg="$(ADB="${MOCK_BIN_DIR}/adb" bash -c "source '${REPO_DIR}/bin/droidlink-device' && droidlink_resolve_device" 2>&1)"
exit_code=$?
set -e
if [[ "${exit_code}" -eq 3 && "${err_msg}" == *"unauthorized"* ]]; then
    echo "PASS"
else
    echo "FAIL (Expected exit code 3, got ${exit_code})"
    exit 1
fi

echo "All multi-device discovery tests passed successfully!"
