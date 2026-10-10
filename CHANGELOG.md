# Changelog

## 2.0.1 — in development

- Bind status and helper health to the current Android boot, and record the root worker's boot and process start time.
- Reject restoration records from a previous boot before signalling a process or writing USB settings. Keep malformed boot records pending and isolate delayed watchdogs from replacement sessions.
- Measure session duration with elapsed time, including sleep, so changing the clock does not change the duration.
- Resolve persisted status and notification actions in the current app language without restarting healthy protection.
- Bound history storage by entries, event length and encoded bytes, redact session tokens, and limit lock waiting. Report failed clearing instead of showing a false success.
- Show first-run setup only while its activity is resumed and the notification permission request has finished.
- Offer Retry after an unexpected helper/watchdog stop instead of misreporting a disconnected display. Only a detach from the current session counts as a normal end.
- Add emulator instrumentation for cross-process history, storage failures and language changes; compile its APK in CI.
- Build version code 21 with the existing update-compatible certificate. Stock Odin 3 update, service restart, recents dismissal, notification Retry and automatic startup after reboot/unlock checks pass; repeated physical touch checks remain pending.

## 2.0.0 — 2026-10-08

- Add a clearer Material You dashboard with live session duration and an honest whole-device battery reading when Android provides one.
- Add a bounded local session history and include it in the redacted support export. History failures never interrupt protection.
- Add a guided first setup with direct access to setup checks, app settings and the Quick Settings tile.
- Add a bounded manual connection, sleep/wake and reconnect checklist. Results are stored without touch coordinates.
- Separate quiet ongoing protection notifications from actionable recovery alerts, while keeping Android's notification controls in charge.
- Add optional Dutch per-app language support while keeping English as the default.
- Raise the release to version code 20 while preserving the existing package and update-compatible signing certificate.

## 1.1.0 — 2026-10-06

- Require fresh helper health and current atomic power observations before showing Protected; noisy logs no longer stop a healthy session.
- Bound automatic helper recovery, verify restoration readback, and preserve snapshots when restoration cannot be confirmed.
- Confirm cleanup of an earlier session before retrying, even when its marker is missing; offer Retry protection in the dashboard and a failure notification.
- Add a Quick Settings tile with updates while the panel is closed, plus setup checks and an Android app-settings shortcut.
- Request notification permission once and preserve help paragraphs.
- Target Android SDK 36, update AppCompat to 1.8.0 and the Gradle wrapper to 9.8.0, and remove the unnecessary launcher-icon API qualifier. Document the narrowly scoped stock-AYN reflection lint exception.
- Add Android CI for every pull request: build the debug APK, run Android lint and the policy/shell regression suite, and retain debug artifacts for seven days.
- Release version code 10 with the existing package and signing certificate for in-place updates.
- Validate the Quick Settings switch and actual notification Retry on stock Odin 3. Final-candidate recording confirms touch after two doze/wake cycles and one display reconnect; the beta's five sleep/wake cycles and two reconnects were also completed by the tester.

## 1.0.0 — 2026-10-05

- Fix Auto protect stopping after display detach on stock Odin 3 firmware: use a short vendor request to a bundled stop helper, and confirm restoration before rearming.
- Add Screen brightness guidance and a shortcut to Android Display settings. Automatic Retroid brightness control is unavailable on the tested setup; use the display’s hardware buttons to match manually.
- Update the 1.0.0 APK in place with version code 7 and the existing signing certificate. Earlier GitHub releases are pre-releases.
- Redesign the English Material You dashboard with a single session action, clear connection status and layouts for portrait and landscape.
- Add opt-in Auto protect: wait for the Retroid display, protect on connection, restore on detach, and prepare the next connection automatically.
- Restore opted-in automatic mode after boot or app updates; Stop also disables automatic mode.
- Preserve the tested USB power guard and protection after dismissing the app. Serialize a replacement service’s launch with the previous service’s restoration.
- Add system/light/dark theme selection and adaptive/themed launcher icons.
- Export bounded app diagnostics through the Android share sheet, with session tokens redacted and no internet permission.
- Read the latest power observation instead of showing an earlier successful sample.
- Publish a non-debuggable release APK with the existing update-compatible certificate.

## 0.5.0 — 2026-10-04

- Keep touch protection running when OdinDuo is swiped away from recent apps.
- Run the foreground service in a separate process, explicitly keep it alive when the activity task is removed, and request automatic Android restarts after unexpected process termination.
- Let a restarted service adopt its existing USB guard during a bounded restart grace without resetting power settings or launching a duplicate guard.
- Share status atomically between the UI and service so reopening the app shows the current session.
- Keep explicit Stop and display-detach restoration working. Wait for a unique root-side acknowledgement before reporting Stop complete, including when cleaning up an older session.
- Add shell lifecycle regression checks for owner loss, PID reuse, adoption, Stop and grace expiry.

## 0.4.0

- Rename the app to OdinDuo.
- Translate the interface and documentation to English.
- Add a Material 3 interface with dynamic system colours and light/dark mode.
- Retain the user-tested, device-scoped USB power guard.
