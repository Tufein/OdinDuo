# Security and maintenance

OdinDuo runs a root helper on the device and writes state that other local apps can read. Strip
account data from diagnostic dumps before posting them; the export contains device and service logs.

Use GitHub private vulnerability reporting **if the repository owner has enabled it**. No separate
confidential contact is configured in this repository. If private reporting is unavailable, open a
minimal issue requesting a private contact, without publishing exploit details.

## What this app trusts

- **The root guard** writes its heartbeat and log under `/data/local/tmp/retroid-touch-logs` with
  mode `0644` (`tools/pserver-power-guard.sh`). Any local reader in that domain can read them and
  try to tamper with them, so treat that directory as public rather than as a trusted channel.
- **Release signing falls back to `build/debug.keystore`** with the published `android` /
  `androiddebugkey` passwords when `ODINDUO_KEYSTORE` is unset (`app/build.gradle`). Published
  releases therefore carry a debug identity: they can only be updated by a build with that same key
  file, and a leak of it would allow update-compatible APKs. A private keystore is the intended fix.
- **`RECEIVE_BOOT_COMPLETED`, foreground services and the battery-optimization exemption** keep the
  touch guard alive. Nothing is uploaded; the app has no network permission.

## Maintenance procedure

- Run `./tools/check.sh` (host regression checks) plus `./gradlew :app:testDebugUnitTest :app:lintDebug`
  before a release; the USB/PServer paths need a physical AYN Odin 3 check.
- Verify the guard restores the original power-management state after every sleep/wake cycle in the
  release notes, and record the firmware version tested.
- Keep `tools/pserver-power-guard.sh` changes under shell-level review: it runs as UID 0 on the device.

This describes the current design, not an audit. No response SLA is promised.
