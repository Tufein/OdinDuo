# OdinDuo roadmap

Priority 1 is **usability**: someone who just plugged in the Retroid Dual Screen should understand what OdinDuo does, see that it works, and recover from problems without reading the README or sharing logs. Reliability, efficiency and device support follow. Every item needs a completion criterion that can be checked, on a physical Odin 3 where hardware is involved.

Status of the current line: 2.0.1 is released; 2.0.2-beta.2 adds resumable onboarding, screen-connection help and display diagnostics (see [docs/releases](docs/releases/)).

## Priority 1: usability

| # | Improvement | Why | Completion criteria |
| --- | --- | --- | --- |
| 1.1 | **One-step updates.** Detect a helper left by an older APK and offer a single "Update protection" action (stop, restore, re-enable Auto protect) instead of the README instruction "Stop protection once and enable Auto protect again" | The most error-prone manual step after every update | After installing over an older version, the dashboard shows one clear prompt; one tap leaves Auto protect on and protection working; verified on a stock Odin 3 |
| 1.2 | **Plain-language status first.** A single headline state ("Protected", "Waiting for screen", "Needs attention") with one suggested next action, and technical detail (PIDs, heartbeat, grace periods) behind a "Details" expander | Status text currently mixes user state and internals | Each state has a headline, one sentence and at most one primary button; English and Dutch strings reviewed together |
| 1.3 | **Unified "Fix it" screen.** Merge Check setup, notification permission, battery restrictions and screen-connection help into one checklist with a status icon and a direct "Fix" button per row | Setup advice is spread over several dialogs and README paragraphs | Every failing check in Check setup has a one-tap route to the right Android settings page; the checklist also appears in shared diagnostics |
| 1.4 | **Clearer Stop vs Auto protect.** Stopping currently also switches Auto protect off. Make this explicit in the button label and confirmation, and offer "Pause until next connection" as a gentler alternative | Surprising side effect, easy to lose protection unintentionally | User tests with two testers show no unexpected loss of Auto protect; wording covered by UI tests |
| 1.5 | **Large text and controller navigation.** Verify the dashboard, dialogs and onboarding at 200% font scale on both screens and with D-pad/gamepad focus order | The target device is a handheld; touch is the thing that breaks | UI tests at large font scale; manual controller pass with focus visible on every control |
| 1.6 | **Readable "Share diagnostics".** Add a short human summary at the top of the export (what was checked, what failed, suggested next step) before the raw logs | Helps users self-diagnose and makes reports easier to triage | Export begins with a summary section; raw bounded logs unchanged; no new data collected |
| 1.7 | **README split.** Move the long technical explanation to `docs/how-it-works.md`; keep README to install, first use, troubleshooting and FAQ | The README is currently the main user manual and is hard to scan | README quick start fits one screen; every earlier README fact still reachable by link |
| 1.8 | **Dutch parity audit.** Review all Dutch strings, including dialogs and notifications, against English | Dutch is already a supported per-app language | No untranslated or mismatched strings; a test that both string files have the same keys |

## Priority 2: confidence and support

| Version | Work | Completion criteria |
| --- | --- | --- |
| 2.1 | Connection and wake checklist with separate passed, failed and not-tested results; easier support summaries | Incomplete checks are not recorded as failures; exports keep useful bounded evidence |
| 2.1 | Waiting-mode measurements and targeted efficiency work | Compare protection off, waiting and connected under matched physical conditions before changing polling or claiming battery impact |
| 2.1 | Stable release of the 2.0.2 beta features after tester feedback and VirusTotal scan | Beta feedback triaged; signing-certificate change documented for upgrading users |

## Priority 3: reach

| Work | Completion criteria |
| --- | --- |
| Firmware compatibility reports | Each supported firmware/USB topology verified on hardware before being listed |
| Other devices | Only after physical evidence; never advertised on assumption |
| Automatic brightness matching | Remains unsupported on the measured Odin 3 + Retroid hardware; revisit only with a working controller path |

## Working agreements

- UI changes get UI tests in `app/ui-tests` and Dutch/English strings in the same change.
- No new permissions; the app keeps no internet access.
- Release notes state what was and was not verified on hardware.
