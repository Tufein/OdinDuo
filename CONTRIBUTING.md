# Contributing to OdinDuo

OdinDuo keeps the Retroid Dual Screen touchscreen working through sleep and wake on the AYN Odin 3.
The app is **Java** (not Kotlin) with one root helper shell script.

## Get oriented

- [README.md](README.md) explains the workaround, what it does not guarantee, and that battery impact
  is unmeasured.
- [docs/design.md](docs/design.md) covers the guard design; [docs/troubleshooting/](docs/troubleshooting)
  and [docs/releases/](docs/releases) hold per-version verification notes.
- [tools/pserver-power-guard.sh](tools/pserver-power-guard.sh) is the part that runs as root on the
  device. Treat changes there as security-relevant and read them aloud in the pull request.

## Build and check

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./tools/check.sh
```

CI additionally builds `:app:assembleDebugAndroidTest`; the instrumentation and physical-device checks
are still run locally, so say which of them you performed.

`app/tests/*.java` are host regression checks that run as plain `main()` programs through
`tools/check.sh` — they are not JUnit and do not appear in `./gradlew test`. When you add one, keep it
runnable from the shell and add it to `tools/check.sh`.

## Conventions

- **Original state is restored, never assumed.** Any change to USB power management must record what
  it found and put that back; the guard's value is that a session ends as it started.
- **Session ownership first.** A second protect run must not fight the first one; keep the token and
  ownership logic intact when touching `VendorBridge` or the guard script.
- **Strings live in `app/res/values/strings.xml` and `values-nl/`** and must stay at parity (same keys,
  same placeholders); there is no automated check yet, so verify both languages in your diff.
- **No claims without a device.** A fix that was not run on an Odin 3 with a dual-screen dock goes in
  the changelog as untested, not as verified.

## Releases

Releases are built from the tag with the same package and signing identity so existing installs can
update in place; an APK checksum accompanies every download. Do not commit private keys. See
[SECURITY.md](SECURITY.md).
