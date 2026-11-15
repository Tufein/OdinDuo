# Pull request checklist

- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passes
- [ ] `./tools/check.sh` passes (host checks in `app/tests/` run as plain `main()` programs, not JUnit)
- [ ] `values/strings.xml` and `values-nl/strings.xml` at parity: same keys, same placeholders
- [ ] Sleep/wake verified on a physical AYN Odin 3 with the dual-screen dock when the guard, the
      Binder adapter or `tools/pserver-power-guard.sh` changed — say so here with the firmware
- [ ] Original power-management state is demonstrably restored (protect → unprotect → reboot path)
- [ ] `CHANGELOG.md` and `ROADMAP.md` updated; unmeasured claims (battery, wake latency) stay unmeasured
- [ ] No keystore or private signing material added; see [SECURITY.md](../SECURITY.md)
