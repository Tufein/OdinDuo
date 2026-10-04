# Changelog

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
