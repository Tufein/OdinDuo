"""Real HTTP integration tests with disposable Mac/cloud repositories."""

import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import urlopen

SPEC = importlib.util.spec_from_file_location("project_sync", Path(__file__).parents[1] / "project-sync.py")
SYNC = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SYNC)


class ProjectSyncTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.mac = Path(self.temp.name) / "mac"
        self.cloud = Path(self.temp.name) / "cloud"
        self.mac.mkdir()
        self.run_git(self.mac, "init", "-q")
        self.run_git(self.mac, "config", "user.email", "test@example.invalid")
        self.run_git(self.mac, "config", "user.name", "Sync test")
        (self.mac / ".gitignore").write_text("build/\n*.keystore\n.odinduo-sync/\n")
        (self.mac / "source.txt").write_text("original\n")
        (self.mac / "runner.sh").write_text("#!/bin/sh\ntrue\n")
        (self.mac / "runner.sh").chmod(0o755)
        self.run_git(self.mac, "add", ".")
        self.run_git(self.mac, "commit", "-qm", "Baseline")
        self.run_git(self.mac, "remote", "add", "origin", "https://github.com/Tufein/OdinDuo.git")
        subprocess.run(["git", "clone", "-q", str(self.mac), str(self.cloud)], check=True)
        self.run_git(self.cloud, "remote", "set-url", "origin", "https://github.com/Tufein/OdinDuo.git")
        self.token = "test-token-with-at-least-32-characters"
        self.server = SYNC.make_server(self.mac, self.token, port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = "http://127.0.0.1:" + str(self.server.server_port)
        self.remote = SYNC.Remote(self.url, self.token, allow_local_http=True)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temp.cleanup()

    @staticmethod
    def run_git(root, *args):
        subprocess.run(["git", "-C", str(root), *args], check=True, capture_output=True)

    def sync(self):
        return SYNC.sync_once(self.cloud, self.remote)

    def test_bootstrap_downloads_mac_changes_and_preserves_executable(self):
        (self.mac / "source.txt").write_text("new Mac source\n")
        (self.mac / "new.txt").write_text("new file\n")
        self.sync()
        self.assertEqual((self.cloud / "source.txt").read_text(), "new Mac source\n")
        self.assertEqual((self.cloud / "new.txt").read_text(), "new file\n")
        self.assertTrue((self.cloud / "runner.sh").stat().st_mode & 0o111)
        backups = list((self.cloud / ".odinduo-sync/backups").rglob("*.bak"))
        self.assertIn(b"original\n", [p.read_bytes() for p in backups])

    def test_upload_then_download_and_restart(self):
        self.sync()
        (self.cloud / "source.txt").write_text("cloud edit\n")
        self.sync()
        self.assertEqual((self.mac / "source.txt").read_text(), "cloud edit\n")
        (self.mac / "source.txt").write_text("next Mac edit\n")
        restarted = SYNC.Remote(self.url, self.token, allow_local_http=True)
        SYNC.sync_once(self.cloud, restarted)
        self.assertEqual((self.cloud / "source.txt").read_text(), "next Mac edit\n")

    def test_preexisting_cloud_changes_are_uploaded_without_reset(self):
        (self.cloud / "source.txt").write_text("uncommitted cloud work\n")
        self.sync()
        self.assertEqual((self.mac / "source.txt").read_text(), "uncommitted cloud work\n")

    def test_conflict_stops_and_preserves_both_versions(self):
        self.sync()
        (self.mac / "source.txt").write_text("Mac edit\n")
        (self.cloud / "source.txt").write_text("cloud edit\n")
        with self.assertRaises(SYNC.Conflict):
            self.sync()
        self.assertEqual((self.mac / "source.txt").read_text(), "Mac edit\n")
        self.assertEqual((self.cloud / "source.txt").read_text(), "cloud edit\n")
        copies = list((self.cloud / ".odinduo-sync/conflicts").rglob("source.txt"))
        self.assertEqual({p.read_text() for p in copies}, {"Mac edit\n", "cloud edit\n"})
        (self.cloud / "source.txt").write_text("Mac edit\n")
        self.sync()  # Resolving by making the two sides equal resumes normally.

    def test_initial_conflict_preserves_uncommitted_work(self):
        (self.mac / "source.txt").write_text("Mac change\n")
        (self.cloud / "source.txt").write_text("cloud change\n")
        with self.assertRaises(SYNC.Conflict):
            self.sync()
        self.assertEqual((self.cloud / "source.txt").read_text(), "cloud change\n")

    def test_deletions_both_directions_are_backed_up(self):
        self.sync()
        (self.cloud / "source.txt").unlink()
        self.sync()
        self.assertFalse((self.mac / "source.txt").exists())
        (self.mac / "runner.sh").unlink()
        self.sync()
        self.assertFalse((self.cloud / "runner.sh").exists())
        self.assertTrue(list((self.mac / ".odinduo-sync/backups").rglob("*.bak")))

    def test_stale_upload_is_rejected(self):
        expected = self.remote.manifest()["source.txt"]
        (self.mac / "source.txt").write_text("changed since manifest\n")
        with self.assertRaises(SYNC.Conflict):
            self.remote.write("source.txt", expected, SYNC.fingerprint(b"stale\n"), b"stale\n")
        self.assertEqual((self.mac / "source.txt").read_text(), "changed since manifest\n")

    def test_deletion_conflicts_with_other_side_edit(self):
        self.sync()
        (self.cloud / "source.txt").unlink()
        (self.mac / "source.txt").write_text("Mac edit must survive\n")
        with self.assertRaises(SYNC.Conflict):
            self.sync()
        self.assertEqual((self.mac / "source.txt").read_text(), "Mac edit must survive\n")
        self.assertFalse((self.cloud / "source.txt").exists())

    def test_cloud_edit_during_download_is_preserved(self):
        self.sync()
        (self.mac / "source.txt").write_text("Mac change\n")
        real_read = self.remote.read
        def competing_edit(name):
            value = real_read(name)
            (self.cloud / name).write_text("new cloud work\n")
            return value
        self.remote.read = competing_edit
        with self.assertRaises(SYNC.Conflict):
            self.sync()
        self.assertEqual((self.cloud / "source.txt").read_text(), "new cloud work\n")
        self.assertEqual((self.mac / "source.txt").read_text(), "Mac change\n")

    def test_private_generated_and_symlink_files_are_excluded(self):
        for name in ["private.keystore", ".env", "credentials.json", "build/output.txt"]:
            path = self.mac / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("excluded\n")
        (self.mac / "link.txt").symlink_to(Path(self.temp.name) / "outside.txt")
        self.assertEqual(set(self.remote.manifest()), {".gitignore", "source.txt", "runner.sh"})
        for name in ["../outside.txt", "/tmp/outside.txt", ".git/config", "private.keystore",
                     ".env", "build/output.txt", "link.txt", "."]:
            with self.subTest(path=name), self.assertRaises(SYNC.SyncError):
                self.remote.write(name, None, SYNC.fingerprint(b"blocked"), b"blocked")

    def test_authentication_required_and_plain_remote_http_rejected(self):
        with self.assertRaises(HTTPError) as error:
            urlopen(self.url + "/v1/manifest", timeout=2)
        self.assertEqual(error.exception.code, 401)
        error.exception.close()
        wrong = SYNC.Remote(self.url, "wrong", allow_local_http=True)
        with self.assertRaises(SYNC.SyncError):
            wrong.manifest()
        with self.assertRaises(SYNC.SyncError):
            SYNC.Remote("http://example.invalid", self.token, allow_local_http=True)

    def test_invalid_checksum_cannot_write(self):
        expected = self.remote.manifest()["source.txt"]
        with self.assertRaises(SYNC.SyncError):
            self.remote.write("source.txt", expected, SYNC.fingerprint(b"correct"), b"wrong")
        self.assertEqual((self.mac / "source.txt").read_text(), "original\n")

    def test_saved_state_cannot_silently_change_service(self):
        self.sync()
        path = self.cloud / ".odinduo-sync/client-state.json"
        value = json.loads(path.read_text())
        value["url"] = "https://another-server.invalid"
        path.write_text(json.dumps(value))
        with self.assertRaises(SYNC.SyncError):
            self.sync()

    def test_executable_mode_changes_sync(self):
        self.sync()
        (self.cloud / "source.txt").chmod(0o755)
        self.sync()
        self.assertTrue((self.mac / "source.txt").stat().st_mode & 0o111)

    def test_exact_repository_is_required(self):
        self.assertEqual(SYNC.validate_root(self.mac), self.mac.resolve())
        self.run_git(self.cloud, "remote", "set-url", "origin", "https://github.com/other/project.git")
        with self.assertRaises(SYNC.SyncError):
            SYNC.validate_root(self.cloud)


if __name__ == "__main__":
    unittest.main()
