#!/usr/bin/env python3
"""Install or stop the project-scoped Mac sync endpoint as a user LaunchAgent."""

import argparse
import hashlib
import json
from pathlib import Path
import os
import plistlib
import subprocess
import sys
import time
from urllib.request import ProxyHandler, Request, build_opener

LABEL = "nl.odinduo.project-sync"


def ready(data, root):
    try:
        token = (data / "OdinDuo.token").read_text().strip()
        request = Request("http://127.0.0.1:8746/v1/manifest",
                          headers={"Authorization": "Bearer " + token})
        with build_opener(ProxyHandler({})).open(request, timeout=2) as response:
            value = json.load(response)
            expected = hashlib.sha256(str(root.resolve()).encode()).hexdigest()
            return value.get("project") == "Tufein/OdinDuo" and value.get("checkout") == expected
    except (OSError, ValueError):
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "install", "status", "stop"])
    options = parser.parse_args()
    if sys.platform != "darwin":
        parser.error("Run this launcher on the Mac only.")
    root = Path(__file__).resolve().parents[1]
    script = root / "tools" / "project-sync.py"
    data = Path.home() / ".local" / "share" / "odinduo-sync"
    plist_path = Path.home() / "Library" / "LaunchAgents" / (LABEL + ".plist")
    domain = "gui/" + str(os.getuid())
    service = domain + "/" + LABEL
    if options.action == "stop":
        result = subprocess.run(["launchctl", "bootout", service], capture_output=True)
        if result.returncode:
            print("Endpoint was not running.")
        else:
            print("Mac endpoint stopped for this login session.")
        pid_path = data / "mac.pid"
        if pid_path.exists():
            pid = int(pid_path.read_text())
            command = subprocess.run(["ps", "-p", str(pid), "-o", "command="],
                                     capture_output=True, text=True).stdout
            if str(data / "endpoint.py") in command and str(root) in command:
                os.kill(pid, 15)
                print("Detached Mac endpoint stopped.")
            pid_path.unlink()
        print("Remove the LaunchAgent plist if it should also stay off at the next login: " + str(plist_path))
        return 0
    if options.action == "status":
        healthy = ready(data, root)
        print("Loopback endpoint verified." if healthy else "Endpoint is not answering.")
        result = subprocess.run(["launchctl", "print", service], capture_output=True, text=True)
        print("LaunchAgent loaded." if result.returncode == 0 else "LaunchAgent is not loaded.")
        for line in result.stdout.splitlines():
            if any(field in line for field in ["state =", "pid =", "last exit code ="]):
                print(line.strip())
        return 0 if healthy else 1
    data.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(data, 0o700)
    plist_path.parent.mkdir(parents=True, exist_ok=True)
    # Load executable code outside the protected Desktop folder at login.
    runtime_script = data / "endpoint.py"
    runtime_script.write_bytes(script.read_bytes())
    os.chmod(runtime_script, 0o600)
    command = [sys.executable, str(runtime_script), "serve", "--root", str(root),
               "--token-file", str(data / "OdinDuo.token"), "--port", "8746"]
    if ready(data, root):
        print("The Mac endpoint is already running. Stop it before reinstalling.")
        return 0 if options.action == "start" else 1
    if options.action == "start":
        with (data / "mac.stdout.log").open("ab") as stdout, (data / "mac.stderr.log").open("ab") as stderr:
            process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=stdout,
                                       stderr=stderr, cwd=data, start_new_session=True)
        pid_path = data / "mac.pid"
        pid_path.write_text(str(process.pid) + "\n")
        os.chmod(pid_path, 0o600)
        for attempt in range(10):
            if ready(data, root):
                print("Mac endpoint started and verified for this login session.")
                print("Private token file: " + str(data / "OdinDuo.token"))
                return 0
            if process.poll() is not None:
                break
            time.sleep(1)
        process.terminate()
        pid_path.unlink(missing_ok=True)
        print("Endpoint did not answer; check logs and macOS project-folder access.", file=sys.stderr)
        return 1
    definition = {
        "Label": LABEL,
        "ProgramArguments": command,
        "WorkingDirectory": str(data),
        "RunAtLoad": True,
        "KeepAlive": True,
        "ThrottleInterval": 10,
        "EnvironmentVariables": {"PATH": "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"},
        "StandardOutPath": str(data / "mac.stdout.log"),
        "StandardErrorPath": str(data / "mac.stderr.log"),
    }
    if plist_path.exists():
        old = plistlib.loads(plist_path.read_bytes())
        old_args = old.get("ProgramArguments", [])
        if old.get("Label") != LABEL or str(root) not in old_args:
            parser.error("An existing LaunchAgent targets another checkout; preserve and review it first.")
    subprocess.run(["launchctl", "bootout", service], capture_output=True)
    plist_path.write_bytes(plistlib.dumps(definition))
    os.chmod(plist_path, 0o600)
    subprocess.run(["launchctl", "bootstrap", domain, str(plist_path)], check=True)
    subprocess.run(["launchctl", "kickstart", service], check=True)
    for attempt in range(10):
        if ready(data, root):
            print("Installed and verified the loopback-only Mac endpoint.")
            break
        time.sleep(1)
    else:
        subprocess.run(["launchctl", "bootout", service], capture_output=True)
        plist_path.unlink(missing_ok=True)
        print("Endpoint did not answer. Check logs and macOS project-folder access.", file=sys.stderr)
        return 1
    print("Cloud network-secret value is stored privately in " + str(data / "OdinDuo.token"))
    print("Endpoint logs: " + str(data))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
