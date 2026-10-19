#!/usr/bin/env bash
# DroidLink CLI Automated Test Suite
# Tests CLI options, syntax, and safe query paths

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TABLET="${REPO_DIR}/bin/tablet"

echo "=== Running DroidLink CLI Tests ==="

# Test 1: Syntax check with bash -n
echo -n "Test 1: Bash syntax verification... "
bash -n "${REPO_DIR}/bin/tablet"
bash -n "${REPO_DIR}/bin/tablet-audio"
bash -n "${REPO_DIR}/bin/tablet-audio-watchdog"
bash -n "${REPO_DIR}/scripts/install.sh"
bash -n "${REPO_DIR}/scripts/uninstall.sh"
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

# Test 4: Installer dry-run
echo -n "Test 4: Installer --dry-run completes cleanly... "
"${REPO_DIR}/scripts/install.sh" --dry-run >/dev/null 2>&1
echo "PASS"

echo "All CLI tests passed successfully!"
