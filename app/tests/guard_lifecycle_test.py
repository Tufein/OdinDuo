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
    script = app / 'power-guard.sh'
    text = source.read_text()
    replacements = [('/data/user/0/nl.retroid.touchguard/files', str(app)),
                     ('/data/local/tmp', str(root / 'data/local/tmp')),
                     ('/sys/', str(root / 'sys') + '/'),
                     ('/proc/', str(proc) + '/'),
                     ('/system/bin/sh', '/bin/sh')]
    for old, new in replacements:
        text = text.replace(old, new)
    script.write_text(text)
    stop_script = app / 'power-stop.sh'
    stop_text = source.with_name('pserver-power-stop.sh').read_text()
    for old, new in replacements:
        stop_text = stop_text.replace(old, new)
    stop_script.write_text(stop_text)
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
                acknowledgement = format(cycle + 1, '032x')
                stopped = subprocess.run(['/bin/sh', str(stop_script), acknowledgement], env=env,
                                         capture_output=True, text=True, timeout=5)
                assert stopped.returncode == 0, 'Restored detach was not acknowledged'
                assert f'STOP acknowledged {acknowledgement} result=0' in log.read_text()
            finally:
                if process.poll() is None:
                    (app / 'guard-enabled').unlink(missing_ok=True)
                    process.wait(timeout=5)
    # Firmware can reject a restore even when the sysfs write reports success.
    # Inject the readback fault through cat while running the real bundled stop path.
    state = root / 'data/local/tmp/retroid-power-guard-state'
    state.mkdir()
    target = controls[0]
    target.write_text('on\n')
    (state / 'count').write_text('1\n')
    (state / 'saved-0').write_text(f'{target}\nauto\nparent\n')
    fault = root / 'reject-restore'
    fault.touch()
    (binaries / 'cat').write_text(
        '#!/bin/sh\n'
        f'if [ -f "{fault}" ] && [ "$1" = "{target}" ]; then echo on; exit 0; fi\n'
        'exec /bin/cat "$@"\n')
    (binaries / 'cat').chmod(0o755)
    rejected_token = 'b' * 32
    rejected = subprocess.run(['/bin/sh', str(stop_script), rejected_token], env=env,
                              capture_output=True, text=True, timeout=5)
    assert rejected.returncode == 1, 'Incorrect restoration readback was acknowledged'
    assert state.is_dir() and (state / 'saved-0').is_file(), 'Readback failure discarded snapshots'
    assert 'RESTORE FAILED readback' in log.read_text(), 'Readback failure was not recorded'
    assert f'STOP acknowledged {rejected_token} result=1' in log.read_text()
    fault.unlink()
    retried_token = 'c' * 32
    retried = subprocess.run(['/bin/sh', str(stop_script), retried_token], env=env,
                             capture_output=True, text=True, timeout=5)
    assert retried.returncode == 0 and not state.exists(), 'Confirmed restoration could not be retried'
    assert target.read_text().strip() == 'auto', 'Retry lost the original setting'
    assert f'STOP acknowledged {retried_token} result=0' in log.read_text()
    # A successful shell exit alone must never acknowledge outstanding restoration snapshots.
    state.mkdir()
    (state / 'saved-0').write_text('pending restoration\n')
    script.write_text('#!/bin/sh\nexit 0\n')
    failed_token = 'f' * 32
    pending = subprocess.run(['/bin/sh', str(stop_script), failed_token], env=env,
                             capture_output=True, text=True, timeout=5)
    assert pending.returncode == 1, 'Pending restoration was reported as successful'
    assert state.is_dir(), 'Failed confirmation discarded snapshots'
    assert f'STOP acknowledged {failed_token} result=1' in log.read_text()
    previous_log = log.read_text()
    invalid = subprocess.run(['/bin/sh', str(stop_script), 'invalid;token'], env=env,
                             capture_output=True, text=True, timeout=5)
    assert invalid.returncode == 2 and log.read_text() == previous_log, 'Invalid token was accepted'
    print('Shell lifecycle checks passed: process loss, PID reuse, adoption, Stop, grace expiry, 3 reconnects, restore readback failure/retry and acknowledgements')
