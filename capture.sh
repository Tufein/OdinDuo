#!/usr/bin/env bash
# Capture a specific Odin; diagnostics stay in the Git-ignored capture directory.
# Usage: ./capture.sh <adb-serial> <label>
set -euo pipefail
cd "$(dirname "$0")"
serial="${1:?provide the Odin ADB serial}"
label="${2:?provide a label, for example before-sleep}"
case "$label" in ''|*[!a-zA-Z0-9_-]*) echo 'Use letters, numbers, underscores or hyphens for the label'; exit 2 ;; esac
python3 - "$serial" "$label" <<'CAPTURE'
from pathlib import Path
import subprocess
import sys
adb = ['adb', '-s', sys.argv[1]]
output = Path('capture') / sys.argv[2]
output.mkdir(parents=True, exist_ok=True)
commands = {
    'input.txt': ['shell', 'dumpsys', 'input'],
    'display.txt': ['shell', 'dumpsys', 'display'],
    'properties.txt': ['shell', 'getprop'],
    'devices.txt': ['shell', 'cat', '/proc/bus/input/devices'],
    'usb.txt': ['shell', 'dumpsys', 'usb'],
    'window-displays.txt': ['shell', 'dumpsys', 'window', 'displays'],
    'logcat.txt': ['logcat', '-d', '-b', 'all'],
}
state = subprocess.run(adb + ['get-state'], capture_output=True, text=True, timeout=4)
if state.returncode or state.stdout.strip() != 'device':
    sys.exit('Device not reachable over ADB.')
for filename, command in commands.items():
    try:
        result = subprocess.run(adb + command, capture_output=True, text=True, timeout=15)
        (output / filename).write_text(result.stdout + result.stderr)
    except subprocess.TimeoutExpired:
        print(f'{filename}: timed out; continuing')
print(f'Saved diagnostics to {output}')
CAPTURE
