# OdinDuo roadmap

The current priority is the [2.0.1 reliability candidate](releases/2.0.1.md). Future scope depends on device testing and community reports.

| Version | Work | Completion criteria |
| --- | --- | --- |
| 2.0.1 | Boot/session identity, watchdog isolation, accurate session timing, safer history, live translations and first-run setup | Local and CI checks plus repeated stock Odin 3 touch, reconnect, recents, upgrade and reboot checks |
| 2.1 | A clearer connection and wake checklist with separate passed, failed and not-tested results; easier support summaries | Users can report incomplete checks without recording them as failures; support exports retain useful bounded evidence |
| 2.1 | Waiting-mode measurements and targeted efficiency improvements | Compare protection off, waiting and connected under matched physical conditions before changing polling or reporting battery impact |
| Later | Firmware compatibility reports, improved controller navigation and large-text layouts | Verify each supported setup and test the actual UI with a controller and larger fonts |

Automatic brightness matching remains unsupported on the measured Odin 3 + Retroid hardware. Broader device support needs physical evidence before it can be advertised.
