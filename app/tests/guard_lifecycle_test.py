#!/usr/bin/env python3
"""Run the real shell guard against fake proc/USB files; never touch host/device power settings."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import time

source = Path(__file__).resolve().parents[2] / 'tools/pserver-power-guard.sh'
with tempfile.TemporaryDirectory(prefix='odinduo-guard-') as directory:
    root = Path(directory).resolve()
    app = root / 'app-files'
    app.mkdir()
    proc = root / 'proc'
    proc.mkdir()
    (proc / 'uptime').write_text('100.0 0.0\n')
    usb = root / 'sys/devices/platform/soc/a600000.ssusb'
    rds = usb / 'a600000.dwc3/xhci-hcd.0.auto/usb1/1-1'
    controls = []
    for node in [usb, usb / 'a600000.dwc3', rds.parent.parent, rds.parent, rds]:
        (node / 'power').mkdir(parents=True, exist_ok=True)
        control = node / 'power/control'
        control.write_text('auto\n')
        controls.append(control)
        (node / 'power/runtime_status').write_text('active\n')
    (rds / 'idVendor').write_text('222a\n')
    (rds / 'idProduct').write_text('0001\n')
    (rds / 'devnum').write_text('2\n')
    bus = root / 'sys/bus/usb/devices'
    bus.mkdir(parents=True)
    (bus / '1-1').symlink_to(rds)
    binaries = root / 'bin'
    binaries.mkdir()
    (binaries / 'id').write_text('#!/bin/sh\n[ "$1" = -u ] && echo 0\n')
    (binaries / 'id').chmod(0o755)
    script = root / 'guard.sh'
    text = source.read_text()
    for old, new in [('/data/user/0/nl.retroid.touchguard/files', str(app)),
                     ('/data/local/tmp', str(root / 'data/local/tmp')),
                     ('/sys/', str(root / 'sys') + '/'),
                     ('/proc/', str(proc) + '/'),
                     ('/system/bin/sh', '/bin/sh')]:
        text = text.replace(old, new)
    script.write_text(text)
    (root / 'data/local/tmp').mkdir(parents=True)
    token = 'a' * 32
    def owner(pid, start):
        path = proc / str(pid)
        path.mkdir(exist_ok=True)
        fields = ['S'] + [str(i) for i in range(1, 19)] + [str(start)] + ['0'] * 20
        (path / 'stat').write_text(f'{pid} (guard service) ' + ' '.join(fields))
        (path / 'status').write_text('Uid:\t10117\t10117\t10117\t10117\n')
        (path / 'cmdline').write_bytes(b'nl.retroid.touchguard:guard\0')
        temporary = app / 'guard-owner.new'
        temporary.write_text(f'{pid}\n{start}\n10117\n{token}\n')
        temporary.replace(app / 'guard-owner')
    def until(predicate, reason, limit=4):
        end = time.monotonic() + limit
        while time.monotonic() < end:
            if predicate(): return
            time.sleep(.05)
        raise AssertionError(reason + '\n' + (app / 'power-guard.txt').read_text())
    owner(44, 1234)
    (app / 'guard-enabled').write_text(token)
    (app / 'guard-heartbeat').touch()
    log = app / 'power-guard.txt'
    env = dict(os.environ, PATH=str(binaries) + os.pathsep + os.environ['PATH'])
    with log.open('w') as output:
        process = subprocess.Popen(['/bin/sh', str(script), '--watch-app', '44', '1234', '10117', token],
                                   stdout=output, stderr=subprocess.STDOUT, env=env, start_new_session=True)
        try:
            until(lambda: 'STATE control=on' in log.read_text(), 'Guard did not pin USB')
            assert all(p.read_text().strip() == 'on' for p in controls)
            shutil.rmtree(proc / '44')
            until(lambda: 'OWNER missing' in log.read_text(), 'Missing owner was not detected')
            assert all(p.read_text().strip() == 'on' for p in controls), 'Power dropped during restart grace'
            # A reused PID with a different start time must not renew the lease.
            owner(44, 9999)
            (app / 'guard-owner').write_text(f'44\n1234\n10117\n{token}\n')
            (proc / 'uptime').write_text('110.0 0.0\n')
            time.sleep(1.1)
            assert 'OWNER resumed' not in log.read_text(), 'Reused PID was accepted'
            owner(45, 1240)
            until(lambda: 'OWNER resumed pid=45' in log.read_text(), 'New service did not adopt the session')
            assert all(p.read_text().strip() == 'on' for p in controls), 'Adoption reset USB power'
            # Explicit Stop must bypass the restart grace.
            (app / 'guard-enabled').unlink()
            until(lambda: 'GUARD stopped' in log.read_text(), 'Explicit Stop did not restore promptly')
            process.wait(timeout=4)
            assert all(p.read_text().strip() == 'auto' for p in controls)
        finally:
            if process.poll() is None:
                (app / 'guard-enabled').unlink(missing_ok=True)
                process.wait(timeout=5)
    # A second session must restore if its owner never returns before the grace expires.
    (proc / 'uptime').write_text('200.0 0.0\n')
    owner(46, 1250)
    (app / 'guard-enabled').write_text(token)
    (app / 'guard-heartbeat').touch()
    with log.open('w') as output:
        process = subprocess.Popen(['/bin/sh', str(script), '--watch-app', '46', '1250', '10117', token],
                                   stdout=output, stderr=subprocess.STDOUT, env=env, start_new_session=True)
        try:
            until(lambda: 'STATE control=on' in log.read_text(), 'Second session did not start')
            shutil.rmtree(proc / '46')
            until(lambda: 'OWNER missing' in log.read_text(), 'Second owner loss was not detected')
            (proc / 'uptime').write_text('261.0 0.0\n')
            until(lambda: 'GUARD stopped' in log.read_text(), 'Expired grace did not stop the guard', limit=6)
            process.wait(timeout=4)
            assert all(p.read_text().strip() == 'auto' for p in controls)
            assert not (app / 'guard-enabled').exists(), 'Expired session marker survived'
        finally:
            if process.poll() is None:
                (app / 'guard-enabled').unlink(missing_ok=True)
                process.wait(timeout=5)
    # A connection monitor must be able to run fresh leases after complete detach restoration.
    # Waiting for HID must leave all original controller settings untouched.
    link = bus / '1-1'
    link.unlink()
    for cycle in range(3):
        owner(50 + cycle, 1300 + cycle)
        (app / 'guard-enabled').write_text(token)
        (app / 'guard-heartbeat').touch()
        with log.open('w') as output:
            process = subprocess.Popen(['/bin/sh', str(script), '--watch-app', str(50 + cycle),
                                        str(1300 + cycle), '10117', token],
                                       stdout=output, stderr=subprocess.STDOUT, env=env, start_new_session=True)
            try:
                until(lambda: 'READY waiting for RDS' in log.read_text(), 'Monitor did not become ready')
                assert all(p.read_text().strip() == 'auto' for p in controls), 'Waiting changed USB power'
                (rds / 'devnum').write_text(str(10 + cycle) + '\n')
                link.symlink_to(rds)
                until(lambda: 'STATE control=on' in log.read_text(), 'Reconnected display was not protected')
                assert all(p.read_text().strip() == 'on' for p in controls)
                link.unlink()
                until(lambda: 'GUARD stopped' in log.read_text(), 'Detach did not finish restoration')
                process.wait(timeout=4)
                assert all(p.read_text().strip() == 'auto' for p in controls), 'Detach did not restore USB power'
                assert not (app / 'guard-enabled').exists(), 'Detached session marker survived'
            finally:
                if process.poll() is None:
                    (app / 'guard-enabled').unlink(missing_ok=True)
                    process.wait(timeout=5)
    print('Shell lifecycle checks passed: process loss, PID reuse, adoption, Stop, grace expiry and 3 reconnects')
