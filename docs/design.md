# OdinDuo interface

The 1.0 interface uses Material 3 semantic colours. Dynamic colours follow the device wallpaper where available; a sage palette is the fallback. System, light and dark appearance are independent of the foreground protection service.

The primary card shows a decorative dual-screen mark, a status chip, the current session, device connection, session duration, whole-device battery availability and one Start/Stop action. The settings card contains Auto protect, its persisted mode, appearance, screen brightness guidance, the connection and wake checklist, session history, setup and help. Cards stack in portrait and share a row in landscape; content scrolls for large fonts or short windows.

Cards use the Material extra-large corner shape, 24 dp padding (20 dp in landscape) and no elevation. Primary actions have a minimum 56 dp height; settings actions and the switch have a minimum 48 dp height. Text and buttons use semantic foreground/background colour pairs, including disabled controls. Decorative icons are omitted from accessibility focus. Status changes are announced politely. Native Material controls retain touch, keyboard and gamepad focus behaviour.

The Auto protect switch controls persistent connection monitoring. Turning it off preserves a fully active session until disconnect. Stop ends the session and disables automatic mode. The dashboard reads atomic service snapshots; navigating, changing theme or dismissing the activity does not stop protection.

Screen brightness opens a native Material dialog with manual matching steps and an Android Display settings shortcut. It makes the Retroid hardware-button requirement explicit and offers no automatic-sync switch or ineffective external-display slider. It does not change brightness, add overlays or request extra permissions.
