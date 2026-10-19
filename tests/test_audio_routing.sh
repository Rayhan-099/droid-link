#!/usr/bin/env bash
# DroidLink Audio Routing & PipeWire Module Test
# Non-destructively verifies module load/unload mechanics

set -euo pipefail

TEST_PORT=4799
echo "=== Running PipeWire Module Routing Test ==="

# 1. Verify pactl is available
if ! command -v pactl >/dev/null 2>&1; then
    echo "pactl not found, skipping runtime audio test."
    exit 0
fi

# 2. Test loading temporary null sink
echo -n "Test 1: Loading isolated test null-sink... "
NULL_MOD=$(pactl load-module module-null-sink sink_name=DroidLink_Test_Sink sink_properties=device.description="DroidLink_Test")
echo "PASS (Module ID: ${NULL_MOD})"

# 3. Test loading simple-protocol-tcp on test port
echo -n "Test 2: Loading module-simple-protocol-tcp on port ${TEST_PORT}... "
TCP_MOD=$(pactl load-module module-simple-protocol-tcp source=DroidLink_Test_Sink.monitor rate=48000 format=s16le channels=2 record=true port=${TEST_PORT})
echo "PASS (Module ID: ${TCP_MOD})"

# 4. Verify socket is listening
echo -n "Test 3: Verifying TCP port ${TEST_PORT} listening... "
if ss -tln 2>/dev/null | grep -q ":${TEST_PORT} "; then
    echo "PASS"
else
    echo "FAIL (Port not listening)"
    pactl unload-module "${TCP_MOD}" 2>/dev/null || true
    pactl unload-module "${NULL_MOD}" 2>/dev/null || true
    exit 1
fi

# 5. Clean teardown
echo -n "Test 4: Unloading test modules... "
pactl unload-module "${TCP_MOD}"
pactl unload-module "${NULL_MOD}"
echo "PASS"

echo "Audio routing module tests passed successfully!"
