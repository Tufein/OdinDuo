# Direct Mac ↔ Codex Cloud source sync

This route synchronizes source files over your private Tailscale connection,
without using GitHub as the file-transfer channel. It needs a Mac endpoint and
a client in **each active cloud task**. It is a custom integration, not a native
Codex folder-sync feature. It has not been validated from a real cloud task yet.

The cloud environment must offer **Advanced → VPN**. Configure its Tailscale
connection, required host/domain access, then republish and start a new task.
Existing tasks keep their own state. See the [official cloud VPN guide](https://learn.chatgpt.com/docs/environments/cloud-environments#private-networking-vpn).

## Mac endpoint

Python 3.9+ and Git are required. In the repository root, start the endpoint for
the current login session:

```sh
python3 tools/start-project-sync-mac.py start
python3 tools/start-project-sync-mac.py status
```

The endpoint listens only on `127.0.0.1:8746`. It requires a random bearer token
stored outside the repository in `~/.local/share/odinduo-sync/OdinDuo.token` with
mode 600. Never commit this file or paste its contents into a chat.

For automatic startup at future logins, stop it and run the launcher's `install`
action. macOS may require project-folder access for that background process;
installation checks the actual endpoint and stops a failed LaunchAgent. Use
`start` from the authorized project terminal if background folder access has
not been granted.

Connect the Mac to Tailscale. After checking that port 443 does not already serve
another application (`tailscale serve status`), expose this endpoint privately:

```sh
tailscale serve --bg --https=443 http://127.0.0.1:8746
```

Record the private HTTPS URL printed by Tailscale. Serve keeps the service inside
the tailnet; do not use Funnel. Preserve any existing Serve routes instead of
overwriting them. Restrict the cloud VPN identity to this Mac's service port in
your tailnet policy. The Mac must be awake and connected for transfers.

## Cloud environment

In **Settings → Codex Cloud → Environments → OdinDuo → Advanced → VPN**, attach a
Tailscale identity with access to the Mac service. The official guide calls for
a reusable, ephemeral auth key. Keep that key in the VPN configuration.

Allow the Mac's exact HTTPS hostname in the environment network policy. Add a
**network secret** named `ODINDUO_SYNC_TOKEN`, using the private token file's
value and that exact hostname as its allowed destination. Do not put the value
in source code, a chat, an install script, or a shell command. The managed HTTPS
proxy substitutes the network-secret placeholder, so the client needs to keep
using the normal proxy. Network-secret substitution requires HTTPS port 443.

After saving and republishing, start a cloud task in the OdinDuo repository.
Replace `https://YOUR-MAC.YOUR-TAILNET.ts.net` below with the private Serve URL:

```sh
export ODINDUO_SYNC_URL='https://YOUR-MAC.YOUR-TAILNET.ts.net'
mkdir -p .odinduo-sync
curl --fail --silent --show-error \
  -H "Authorization: Bearer $ODINDUO_SYNC_TOKEN" \
  "$ODINDUO_SYNC_URL/v1/client" -o .odinduo-sync/client.py
python3 .odinduo-sync/client.py client --root "$PWD" \
  --url "$ODINDUO_SYNC_URL" --once
```

The first pass compares both working trees with the cloud checkout's Git HEAD.
Unchanged files can receive Mac changes, and existing cloud edits can be sent
back when the Mac still matches their baseline. Conflicting edits stop the pass
without overwriting either original. Review the pass before leaving it running:

```sh
nohup python3 .odinduo-sync/client.py client --root "$PWD" \
  --url "$ODINDUO_SYNC_URL" > .odinduo-sync/client.log 2>&1 &
echo $! > .odinduo-sync/client.pid
```

The client checks every three seconds while the cloud process is running.
Suspended/stopped cloud tasks do not provide an always-on sync service. Each new
task needs its own client. The reusable environment setup is not updated by file
transfers in a task.

## Verify and resolve conflicts

Create an ordinary source file locally and check that it arrives in the cloud;
edit it in the cloud and check that the Mac receives the change. Check
`.odinduo-sync/client-state.json` for a recent `last_success` and the client log
for transfers. Mac `.odinduo-sync/last-client.json` records the last connection.

If both sides changed a file, the client exits with code 2 and saves both copies
under cloud `.odinduo-sync/conflicts/`. Resolve explicitly by making both originals
equal, then restart the client. Every overwrite/deletion saves up to 20 prior
copies per path under `.odinduo-sync/backups/` on the side receiving the change.
An unavailable endpoint pauses transfers and preserves unsent files.

Git metadata and branch pointers are not synchronized. Pause the cloud client
before switching branches, pulling, rebasing, or restoring files in either
checkout. Use one active editing location at a time for coherent changes across
multiple files. Transfers are atomic per file, not across a whole code change.
Git remains useful for history and review.

## Scope and stopping

The endpoint only accepts the `Tufein/OdinDuo` repository. Git-ignored files,
`.git/`, sync state, builds, captures, credentials, environment files, signing
keys and symlinks are excluded. Individual source files are limited to 32 MiB.
Uploads require a matching previous checksum; stale writes are rejected.

Stop the cloud client with `kill "$(cat .odinduo-sync/client.pid)"`. Stop the Mac
endpoint with `python3 tools/start-project-sync-mac.py stop`. To keep it off at
future logins, remove its user LaunchAgent plist as printed by the launcher.
Remove only this endpoint's Tailscale Serve route when retiring the integration.

Integration tests use disposable repositories and loopback HTTP only:

```sh
python3 -m unittest discover -s tools/tests -p 'test_project_sync.py' -v
```
