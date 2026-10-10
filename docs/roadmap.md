# OdinDuo roadmap

The [2.0.1 reliability release](releases/2.0.1.md) is complete. The next priority is clearer test reporting and measured waiting-mode efficiency. Future scope depends on device testing and community reports.

| Version | Work | Completion criteria |
| --- | --- | --- |
| 2.0.1 — released | Boot/session identity, watchdog isolation, accurate session timing, safer history, live translations and first-run setup | Local/CI and stock Odin lifecycle checks pass; tester confirms repeated touch/wake/reconnect. Recording limits are documented in the release notes |
| 2.1 | A clearer connection and wake checklist with separate passed, failed and not-tested results; easier support summaries | Users can report incomplete checks without recording them as failures; support exports retain useful bounded evidence |
| 2.1 | Waiting-mode measurements and targeted efficiency improvements | Compare protection off, waiting and connected under matched physical conditions before changing polling or reporting battery impact |
| Later | Firmware compatibility reports, improved controller navigation and large-text layouts | Verify each supported setup and test the actual UI with a controller and larger fonts |

Automatic brightness matching remains unsupported on the measured Odin 3 + Retroid hardware. Broader device support needs physical evidence before it can be advertised.
