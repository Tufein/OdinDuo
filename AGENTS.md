# AGENTS.md — OdinDuo

Instructions for coding agents (OpenCode, Claude Code, Codex and similar). `CONTRIBUTING.md` is
loaded alongside this file through `opencode.json`. OpenCode also picks up the skill in
`.claude/skills/` (Claude Code compatible).

## Project

Android app that keeps the **Retroid Dual Screen** touchscreen working through sleep/wake on the
**AYN Odin 3**, by applying a temporary USB power-management workaround through the stock AYN service.
Material 3 dashboard, English with optional Dutch per-app language.

- Package / application id: `nl.retroid.touchguard`
- Version: `app/build.gradle`, currently 2.0.2-beta.3 / 24
- compileSdk 36, minSdk 33, targetSdk 36, JDK 21 (CI); host checks compile with `--release 17`
- Language: **Java**, Groovy Gradle

## Layout (non-standard)

| Path | What lives there |
| --- | --- |
| `app/src/nl/retroid/touchguard/` | App sources (no `main/java` folder) |
| `app/AndroidManifest.xml`, `app/res/` | Manifest and resources (`values-nl` for Dutch) |
| `app/tests/*.java` | Host regression checks run as plain `main()` programs by `tools/check.sh` (not JUnit) |
| `app/androidTest/`, `app/ui-tests/` | Instrumentation and UI tests |
| `tools/` | `check.sh`, device logging/probe and recovery test scripts |
| `docs/releases/` | Per-version release and verification notes; `docs/roadmap.md` |

## Build and check

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./tools/check.sh                       # host regression checks
./gradlew :app:assembleDebugAndroidTest   # CI also builds this
```

## Rules

- When you add a host check in `app/tests/`, also add it to `tools/check.sh`.
- Keep English and Dutch strings in sync (same placeholders); there is no automated check.
- Mark device behaviour you could not verify as untested in the changelog, never as verified.
- Never commit private signing keys.

## Release process

Releases are built from the tag with the same package and signing identity so installs update in
place; every APK ships with a checksum. Notes go in `docs/releases/<version>.md` and `CHANGELOG.md`.
The owner always wants work shipped as a GitHub release with notes that explain what changed in the code.
