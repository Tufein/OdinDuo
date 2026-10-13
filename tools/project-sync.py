#!/usr/bin/env python3
"""Project-scoped file sync over authenticated HTTPS; no third-party packages."""

import argparse
import base64
import binascii
import contextlib
import hashlib
import hmac
import json
import os
from pathlib import Path, PurePosixPath
import secrets
import stat
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

PROJECT = "Tufein/OdinDuo"
MAX_FILE = 32 * 1024 * 1024
MAX_BODY = 45 * 1024 * 1024
STATE_DIR = ".odinduo-sync"
BLOCKED_PARTS = {".git", STATE_DIR, "capture", "build", ".gradle", ".idea",
                 "__pycache__", ".venv", "node_modules"}
BLOCKED_SUFFIXES = {".apk", ".keystore", ".jks", ".p12", ".pfx", ".pem", ".key"}


class SyncError(Exception):
    pass


class Conflict(SyncError):
    pass


class Retry(SyncError):
    pass


def git(root, *args, check=True, input_data=None):
    result = subprocess.run(["git", "-C", str(root), *args], capture_output=True, input=input_data)
    if check and result.returncode:
        raise SyncError("Git operation failed: " + args[0])
    return result


def validate_root(root):
    root = Path(root).resolve()
    actual = git(root, "rev-parse", "--show-toplevel").stdout.decode().strip()
    if Path(actual).resolve() != root:
        raise SyncError("Use the repository root as --root.")
    remote = git(root, "remote", "get-url", "origin").stdout.decode().strip()
    if remote.rstrip("/").removesuffix(".git") not in {
        "https://github.com/" + PROJECT, "git@github.com:" + PROJECT,
        "ssh://git@github.com/" + PROJECT,
    }:
        raise SyncError("This tool only synchronizes " + PROJECT)
    return root


def permitted(root, name, check_ignore=True):
    if not isinstance(name, str) or not name or "\\" in name or "\x00" in name:
        return False
    pure = PurePosixPath(name)
    if not pure.parts or pure.is_absolute() or str(pure) != name or any(p in {".", ".."} for p in pure.parts):
        return False
    if any(p in BLOCKED_PARTS for p in pure.parts):
        return False
    leaf = pure.name.lower()
    if (leaf.startswith((".env", "credentials", "secret", "token"))
            or leaf in {"local.properties", ".ds_store", "auth.json"}
            or pure.suffix.lower() in BLOCKED_SUFFIXES):
        return False
    if check_ignore:
        checked = git(root, "check-ignore", "--no-index", "--", name, check=False)
        if checked.returncode not in {0, 1}:
            raise SyncError("Could not verify excluded files; synchronization stopped.")
        if checked.returncode == 0:
            return False
    return True


def safe_path(root, name, check_ignore=True):
    if not permitted(root, name, check_ignore):
        raise SyncError("Excluded or invalid path.")
    path = root
    for part in PurePosixPath(name).parts:
        path = path / part
        if path.is_symlink():
            raise SyncError("Symlinks are excluded.")
    return path


def fingerprint(data, executable=False):
    return {"sha256": hashlib.sha256(data).hexdigest(), "executable": bool(executable)}


def read_file(root, name, check_ignore=True):
    path = safe_path(root, name, check_ignore)
    try:
        info = path.stat()
        if not stat.S_ISREG(info.st_mode):
            raise SyncError("Only regular files can be synchronized.")
        if info.st_size > MAX_FILE:
            raise SyncError("File exceeds 32 MiB: " + name)
        with path.open("rb") as stream:
            data = stream.read(MAX_FILE + 1)
        if len(data) > MAX_FILE:
            raise SyncError("File exceeds 32 MiB: " + name)
        return fingerprint(data, info.st_mode & 0o111), data
    except FileNotFoundError:
        return None, None


def scan(root):
    result = {}
    raw = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z").stdout
    names = set(n.decode("utf-8") for n in raw.split(b"\0") if n)
    checked = git(root, "check-ignore", "--no-index", "--stdin", "-z",
                  check=False, input_data=raw)
    if checked.returncode not in {0, 1}:
        raise SyncError("Could not verify excluded files; synchronization stopped.")
    excluded = set(n.decode("utf-8") for n in checked.stdout.split(b"\0") if n)
    for name in names - excluded:
        if not permitted(root, name, check_ignore=False):
            continue
        try:
            meta, _ = read_file(root, name, check_ignore=False)
        except SyncError as exc:
            if "Symlinks" in str(exc):
                continue
            raise
        if meta is not None:
            result[name] = meta
    if len(result) > 10000:
        raise SyncError("Project exceeds 10,000 source files.")
    return result


def atomic_file(path, data, mode=0o600):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=".sync-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def json_file(path, value):
    atomic_file(path, json.dumps(value, sort_keys=True, indent=2).encode() + b"\n")


def backup(root, name, meta, data):
    if meta is None:
        return
    key = hashlib.sha256(name.encode()).hexdigest()
    folder = root / STATE_DIR / "backups" / key
    stem = str(time.time_ns())
    atomic_file(folder / (stem + ".bak"), data)
    json_file(folder / (stem + ".json"), {"path": name, "fingerprint": meta})
    for older in sorted(folder.glob("*.bak"))[:-20]:
        older.unlink()
        older.with_suffix(".json").unlink(missing_ok=True)


def write_file(root, name, expected, replacement, data):
    path = safe_path(root, name)
    current, original = read_file(root, name)
    if current != expected:
        raise Conflict("File changed before write: " + name)
    if replacement is not None:
        if (not isinstance(replacement, dict) or not isinstance(data, bytes)
                or len(data) > MAX_FILE
                or fingerprint(data, replacement.get("executable")) != replacement):
            raise SyncError("Invalid file checksum or mode.")
    backup(root, name, current, original)
    # Recheck after saving the backup, including symlinks and ignore changes.
    path = safe_path(root, name)
    if read_file(root, name)[0] != expected:
        raise Conflict("File changed during write: " + name)
    if replacement is None:
        path.unlink(missing_ok=True)
    else:
        atomic_file(path, data, 0o755 if replacement["executable"] else 0o644)


def load_token(path, create=False):
    path = Path(path).expanduser()
    if create and not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as stream:
            stream.write(secrets.token_urlsafe(32) + "\n")
    if path.is_symlink() or path.stat().st_mode & 0o077:
        raise SyncError("Token file must be private (chmod 600) and not a symlink.")
    token = path.read_text().strip()
    if len(token) < 32:
        raise SyncError("Token must contain at least 32 characters.")
    return token


def make_server(root, token, port=8746):
    lock = threading.RLock()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass  # Do not log headers, file contents or credentials.

        def reply(self, status, value):
            body = json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def authorized(self):
            supplied = self.headers.get("Authorization", "").encode()
            if not hmac.compare_digest(supplied, ("Bearer " + token).encode()):
                self.reply(401, {"error": "Unauthorized"})
                return False
            return True

        def do_GET(self):
            if not self.authorized():
                return
            try:
                address = urlsplit(self.path)
                with lock:
                    if address.path == "/v1/manifest":
                        json_file(root / STATE_DIR / "last-client.json",
                                  {"time": time.time(), "action": "connected"})
                        checkout = hashlib.sha256(str(root.resolve()).encode()).hexdigest()
                        self.reply(200, {"version": 1, "project": PROJECT,
                                         "checkout": checkout, "files": scan(root)})
                    elif address.path == "/v1/file":
                        name = parse_qs(address.query).get("path", [""])[0]
                        meta, data = read_file(root, name)
                        self.reply(200, {"fingerprint": meta, "data":
                                        base64.b64encode(data).decode() if data is not None else None})
                    elif address.path == "/v1/client":
                        body = Path(__file__).read_bytes()
                        self.send_response(200)
                        self.send_header("Content-Type", "text/x-python")
                        self.send_header("Content-Length", str(len(body)))
                        self.send_header("Cache-Control", "no-store")
                        self.end_headers()
                        self.wfile.write(body)
                    else:
                        self.reply(404, {"error": "Unknown endpoint"})
            except SyncError as exc:
                self.reply(400, {"error": str(exc)})
            except (OSError, ValueError):
                self.reply(500, {"error": "File operation failed"})

        def do_POST(self):
            if not self.authorized():
                return
            try:
                if self.path != "/v1/file":
                    self.reply(404, {"error": "Unknown endpoint"})
                    return
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= MAX_BODY:
                    raise SyncError("Invalid request size.")
                item = json.loads(self.rfile.read(length))
                name = item["path"]
                meta = item["fingerprint"]
                data = base64.b64decode(item["data"], validate=True) if meta is not None else None
                with lock:
                    write_file(root, name, item["expected"], meta, data)
                    json_file(root / STATE_DIR / "last-client.json",
                              {"time": time.time(), "path": name, "action": "upload"})
                    self.reply(200, {"fingerprint": meta})
            except Conflict as exc:
                self.reply(409, {"error": str(exc)})
            except (SyncError, ValueError, KeyError, TypeError, binascii.Error) as exc:
                self.reply(400, {"error": str(exc) if isinstance(exc, SyncError) else "Invalid request"})
            except OSError:
                self.reply(500, {"error": "File operation failed"})

    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    server.daemon_threads = True
    return server


class Remote:
    def __init__(self, url, token, allow_local_http=False):
        parts = urlsplit(url)
        if (parts.scheme != "https" and not (
                allow_local_http and parts.scheme == "http"
                and parts.hostname in {"127.0.0.1", "localhost"})):
            raise SyncError("Use a private HTTPS URL (Tailscale Serve).")
        if parts.username or parts.password or parts.query or parts.fragment or parts.path not in {"", "/"}:
            raise SyncError("Use the service's base URL, without credentials or a path.")
        self.url, self.token = url.rstrip("/"), token
        class NoRedirect(HTTPRedirectHandler):
            def redirect_request(self, *args):
                return None
        self.opener = build_opener(NoRedirect())

    def request(self, route, payload=None):
        body = json.dumps(payload).encode() if payload is not None else None
        request = Request(self.url + route, data=body, headers={
            "Authorization": "Bearer " + self.token, "Content-Type": "application/json"})
        try:
            with self.opener.open(request, timeout=30) as response:
                raw = response.read(MAX_BODY + 1)
            if len(raw) > MAX_BODY:
                raise SyncError("Server response exceeds limit.")
            return json.loads(raw)
        except HTTPError as exc:
            code = exc.code
            exc.close()
            if code == 409:
                raise Conflict("Remote file changed; retry the sync pass.") from None
            raise SyncError("Sync server returned HTTP " + str(code)) from None
        except (URLError, TimeoutError, OSError):
            raise Retry("Sync server is unavailable; files were preserved.") from None
        except ValueError:
            raise SyncError("Invalid server response.") from None

    def manifest(self):
        value = self.request("/v1/manifest")
        if value.get("project") != PROJECT or value.get("version") != 1:
            raise SyncError("Wrong project or protocol.")
        self.checkout = value["checkout"]
        return value["files"]

    def read(self, name):
        value = self.request("/v1/file?" + urlencode({"path": name}))
        meta = value["fingerprint"]
        data = base64.b64decode(value["data"], validate=True) if meta is not None else None
        if meta is not None and fingerprint(data, meta.get("executable")) != meta:
            raise SyncError("Download checksum mismatch.")
        return meta, data

    def write(self, name, expected, meta, data):
        result = self.request("/v1/file", {"path": name, "expected": expected,
                              "fingerprint": meta, "data": base64.b64encode(data).decode()
                              if data is not None else None})
        if result.get("fingerprint") != meta:
            raise SyncError("Upload was not acknowledged.")


def initial_baseline(root, names):
    result = {}
    modes = {}
    for entry in git(root, "ls-tree", "-r", "-z", "HEAD").stdout.split(b"\0"):
        if entry:
            info, name = entry.split(b"\t", 1)
            mode, kind, _ = info.split()
            if kind == b"blob" and mode != b"120000":
                modes[name.decode()] = mode == b"100755"
    for name in names:
        if name not in modes or not permitted(root, name):
            continue
        data = git(root, "show", "HEAD:" + name).stdout
        result[name] = fingerprint(data, modes[name])
    return result


def keep_conflict(root, name, local, remote):
    folder = root / STATE_DIR / "conflicts" / str(time.time_ns())
    for side, (meta, data) in {"cloud": local, "mac": remote}.items():
        if meta is not None:
            atomic_file(folder / side / name, data)
    json_file(folder / "conflict.json", {"path": name, "cloud": local[0], "mac": remote[0]})
    return str(folder)


def sync_once(root, remote):
    remote_files = remote.manifest()
    local_files = scan(root)
    state_path = root / STATE_DIR / "client-state.json"
    if state_path.exists():
        state = json.loads(state_path.read_text())
        if (state.get("project") != PROJECT or state.get("url") != remote.url
                or state.get("checkout") != remote.checkout):
            raise SyncError("Saved sync state belongs to another service; preserve it and review setup.")
        baseline = state["files"]
    else:
        baseline = initial_baseline(root, set(local_files) | set(remote_files))
        state = {"project": PROJECT, "url": remote.url,
                 "checkout": remote.checkout, "files": baseline}
    actions = []
    for name in sorted(set(baseline) | set(local_files) | set(remote_files)):
        if not permitted(root, name):
            continue
        current = local_files.get(name)
        incoming = remote_files.get(name)
        previous = baseline.get(name)
        if current == incoming:
            baseline[name] = current
            continue
        if current == previous:
            observed, data = remote.read(name)
            if observed != incoming:
                raise Retry("Mac file changed during download; retrying.")
            write_file(root, name, current, observed, data)
            baseline[name] = observed
            actions.append("download " + name)
        elif incoming == previous:
            observed, data = read_file(root, name)
            if observed != current:
                raise Retry("Cloud file changed during upload; retrying.")
            remote.write(name, incoming, observed, data)
            baseline[name] = observed
            actions.append("upload " + name)
        else:
            saved = keep_conflict(root, name, read_file(root, name), remote.read(name))
            json_file(state_path, state)
            raise Conflict("Both sides changed " + name + "; originals preserved. Copies: " + saved)
        state["last_success"] = time.time()
        json_file(state_path, state)
    state["last_success"] = time.time()
    json_file(state_path, state)
    return actions


@contextlib.contextmanager
def single_process(root, role):
    import fcntl
    path = root / STATE_DIR / (role + ".lock")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SyncError("Another " + role + " is already running for this checkout.") from None
        yield


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    serve = subcommands.add_parser("serve", help="Run Mac endpoint, bound to loopback only")
    serve.add_argument("--root", type=Path, default=Path.cwd())
    serve.add_argument("--port", type=int, default=8746)
    serve.add_argument("--token-file", type=Path, required=True)
    client = subcommands.add_parser("client", help="Run in the cloud checkout; synchronize both directions")
    client.add_argument("--root", type=Path, default=Path.cwd())
    client.add_argument("--url", required=True)
    client.add_argument("--token-env", default="ODINDUO_SYNC_TOKEN")
    client.add_argument("--interval", type=float, default=3)
    client.add_argument("--once", action="store_true")
    client.add_argument("--allow-local-http", action="store_true", help="Loopback integration tests only")
    arguments = parser.parse_args()
    try:
        root = validate_root(arguments.root)
        if arguments.command == "serve":
            token_path = arguments.token_file.expanduser().resolve()
            if token_path.is_relative_to(root):
                raise SyncError("Keep the token file outside the project.")
            token = load_token(arguments.token_file, create=True)
            with single_process(root, "server"):
                server = make_server(root, token, arguments.port)
                print("Mac endpoint ready on loopback port " + str(server.server_port), flush=True)
                server.serve_forever()
        else:
            if arguments.interval < 1:
                raise SyncError("Polling interval must be at least one second.")
            token = os.environ.get(arguments.token_env, "")
            if not token:
                raise SyncError("Missing cloud network secret: " + arguments.token_env)
            remote = Remote(arguments.url, token, arguments.allow_local_http)
            with single_process(root, "client"):
                while True:
                    try:
                        actions = sync_once(root, remote)
                        for action in actions:
                            print(action, flush=True)
                        if arguments.once:
                            print("Sync pass verified.", flush=True)
                            return 0
                    except Retry as exc:
                        if arguments.once:
                            raise
                        print(str(exc), file=sys.stderr, flush=True)
                    time.sleep(arguments.interval)
    except Conflict as exc:
        print("SYNC STOPPED: " + str(exc), file=sys.stderr)
        return 2
    except (SyncError, OSError, ValueError) as exc:
        print("SYNC ERROR: " + str(exc), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
