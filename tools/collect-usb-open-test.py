#!/usr/bin/env python3
"""Collect an RDS experiment, with bounded waits when the Odin is disconnected."""
import argparse
from datetime import datetime
from pathlib import Path
import re
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True, help='ADB serial of the Odin to collect from')
parser.add_argument('--kind', choices=('open', 'data', 'power', 'app'), default='open')
args = parser.parse_args()
adb = ['adb', '-s', args.serial]

def run(*arguments, timeout=10):
    return subprocess.run(adb + list(arguments), capture_output=True, text=True, timeout=timeout)

try:
    state = run('get-state', timeout=4)
    if state.returncode or state.stdout.strip() != 'device':
        print('Odin is not reachable over ADB. Connect it to your computer and try again.')
        sys.exit(1)
    if args.kind == 'app':
        remote = 'files/power-guard.txt'
    elif args.kind == 'power':
        remote = '/data/local/tmp/retroid-touch-logs/power-guard.txt'
    else:
        latest = run('shell', 'cat', f'/data/local/tmp/retroid-touch-logs/usb-{args.kind}-latest')
        remote = latest.stdout.strip()
        if latest.returncode or not re.fullmatch(rf'/data/local/tmp/retroid-touch-logs/usb-{args.kind}-\d{{8}}-\d{{6}}\.txt', remote):
            print('No valid USB test log found.')
            sys.exit(1)
    result = (run('shell', 'run-as', 'nl.retroid.touchguard', 'cat', remote)
              if args.kind == 'app' else run('shell', 'cat', remote))
    if result.returncode:
        print('Could not read the test log:', result.stderr.strip())
        sys.exit(1)
    root = Path(__file__).resolve().parent.parent
    output = root / 'capture' / (f'usb-{args.kind}-result-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
    output.mkdir(parents=True, exist_ok=True)
    (output / f'usb-{args.kind}.txt').write_text(result.stdout)
    successful = result.stdout.count('read-only USB open succeeded')
    failed = result.stdout.count('read-only USB open unavailable')
    wakes = result.stdout.count('screen awake=true')
    print(result.stdout)
    if args.kind == 'open':
        print(f'Recorded wakes: {wakes}; USB opens succeeded: {successful}; denied/failed: {failed}')
    elif args.kind == 'data':
        print(f'Recorded wakes: {wakes}; USB data disabled: {result.stdout.count("USB data off result=0")}; USB data restored: {result.stdout.count("USB data restore result=0")}')
        watchdog = run('shell', 'cat', '/data/local/tmp/retroid-touch-logs/usb-data-restore.txt')
        if not watchdog.returncode:
            (output / 'usb-data-restore.txt').write_text(watchdog.stdout)
    else:
        print(f'USB settings pinned: {result.stdout.count("PIN ")}; original values restored: {result.stdout.count("RESTORE ")}; failures: {result.stdout.count("FAILED")}')
        if args.kind == 'app':
            for source, target in [('files/guard-events.txt', 'app-events.txt'), ('files/guard-status.json', 'app-status.json'), ('files/guard-owner', 'app-owner.txt'), ('files/guard-heartbeat', 'app-heartbeat.txt')]:
                data = run('shell', 'run-as', 'nl.retroid.touchguard', 'cat', source)
                if not data.returncode:
                    (output / target).write_text(data.stdout)
            services = run('shell', 'dumpsys', 'activity', 'services', 'nl.retroid.touchguard')
            if not services.returncode:
                (output / 'app-services.txt').write_text(services.stdout)
    print(f'Saved: {output}')
    latest_raw = run('shell', 'cat', '/data/local/tmp/retroid-touch-logs/latest')
    raw = latest_raw.stdout.strip()
    if not latest_raw.returncode and re.fullmatch(r'/data/local/tmp/retroid-touch-logs/raw-\d{8}-\d{6}', raw):
        pulled = run('pull', raw, str(output / 'device-logger'), timeout=60)
        print(pulled.stdout.strip() or pulled.stderr.strip())
    logcat = run('logcat', '-d', '-b', 'all', timeout=15)
    if not logcat.returncode:
        (output / 'logcat.txt').write_text(logcat.stdout)
except subprocess.TimeoutExpired:
    print('Connection timed out; logs already collected have been preserved.')
    sys.exit(1)
