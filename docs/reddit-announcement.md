# OdinDuo 1.0.0: keep Retroid Dual Screen touch working on the AYN Odin 3

I kept losing touch input on my Retroid Dual Screen after putting my Odin 3 to sleep or switching apps. The display still worked, but the touchscreen stopped responding.

OdinDuo is a small, free, open-source app built around the USB power-management workaround that solved this on my setup. Version 1.0.0 is now available.

**What it does**

- Keeps the Retroid touchscreen's USB connection awake while protection is active.
- Continues protecting it when you return to your launcher or swipe OdinDuo out of recent apps.
- Offers optional **Auto protect**: enable it once, and it waits for the screen, protects each connection and stays ready when you unplug it.
- Restores the original USB power settings when you unplug the screen or stop protection.
- Includes an English Material You interface, system/light/dark themes and local diagnostics you can choose to share.
- Includes a brightness-matching guide and a shortcut to Android Display settings. The Retroid screen still uses its own brightness buttons; automatic brightness sync is not supported on my tested setup.

**Getting started**

1. Download and install the APK from the release below.
2. Open OdinDuo and enable **Auto protect**, or tap **Start protection** for one session.
3. Connect the Retroid Dual Screen, wait for **Protected**, then go back to your launcher or game.

It works through the built-in AYN service on the tested stock firmware. You do not need Magisk, a bootloader unlock or a firmware flash. The app has no internet permission and sends nothing automatically.

I tested the touch workaround on an **AYN Odin 3 running Android 15, firmware 1.0.0.187**. Other devices and firmware versions are unverified. Keeping USB awake may use extra battery, including during sleep; I haven't measured the battery impact yet. Start protection while touch is working: this is a prevention workaround, not a guaranteed recovery tool after touch has already failed.

**Download:** https://github.com/Tufein/OdinDuo/releases/tag/v1.0.0

**Source and issues:** https://github.com/Tufein/OdinDuo

Thanks to Crypt0Shmipt0 for the original Odin 3 USB-pinning approach, and AurelioB's ClusterTune / FeralAI's O2P Tweaks for documenting the stock AYN service. OdinDuo builds on that work with session management, restoration and automatic connection handling.

If you try it, I'd appreciate hearing your Odin model, firmware version and how it behaves after sleep and wake. This is an independent community project, not an official AYN or Retroid app.
