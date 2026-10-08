# Fix for Retroid Dual Screen touch dropping after sleep on the AYN Odin 3 — OdinDuo

If your Retroid Dual Screen still shows an image but touch stops working after sleep or switching apps, I had the same problem on my Odin 3.

I made **OdinDuo**, a free, open-source app that keeps the Retroid touchscreen’s USB connection awake while protection is running. This USB power-management workaround solved the touch dropouts on my setup. It keeps running when you go back to your launcher or swipe the app out of recent apps, and restores the original USB power settings when you unplug the screen or stop protection.

**How to use the fix**

1. Download and install the APK.
2. Open OdinDuo and enable **Auto protect**, or tap **Start protection** for one session.
3. Connect your Retroid Dual Screen and wait for **Protected**.
4. Go back to your game. Auto protect stays ready when you unplug and reconnect the screen.

**Full release download:** [OdinDuo 2.0.0](https://github.com/Tufein/OdinDuo/releases/tag/v2.0.0)

Thanks for all the feedback and support so far. **2.0.0** adds:

- Session duration and the Odin's battery level on the dashboard.
- A bounded local session history that you can clear or include in shared diagnostics.
- A first-run setup check and a direct shortcut to add the existing Quick Settings tile.
- A manual connection, sleep/wake and reconnect checklist that stores your results without touch coordinates.
- A separate recovery-alert notification channel with the existing **Retry protection** action.
- Dutch through Android's per-app language settings, alongside English.

It retains 1.1.0's helper-health checks, bounded automatic recovery, confirmed USB restoration, Quick Settings control and protection after closing recent apps.

**Updating:** Stop protection, install 2.0.0 over your existing OdinDuo installation and re-enable Auto protect. The package and signing certificate stay the same, and the release includes an APK checksum.

**Testing status:** the 2.0.0 builds, lint and GitHub checks pass. The new version has not yet had a physical Odin test; the earlier successful sleep/wake touch tests were on 1.1.0. Please include your app version when reporting results.

The workaround was tested on **AYN Odin 3, Android 15, firmware 1.0.0.187**. It uses the built-in AYN service: no Magisk, bootloader unlock or firmware flash needed on that setup. Other devices and firmware versions are unverified. Start protection while touch still works; it is not a guaranteed way to recover touch after it has already failed. Keeping USB awake may use extra battery, which I haven’t measured yet.

The app has a Material You interface in English and Dutch and no internet permission. Brightness matching is manual using the Retroid screen’s buttons; automatic brightness sync is unavailable on my tested setup.

**Source / report a problem:** [Tufein/OdinDuo](https://github.com/Tufein/OdinDuo). Please include your model, firmware, app version and what happened. You can export logs through **Help & diagnostics → Share diagnostics**.

Credit to Crypt0Shmipt0 for the USB-pinning approach, and AurelioB’s ClusterTune / FeralAI’s O2P Tweaks for documenting the stock AYN service. This is an independent community project, not an official AYN or Retroid app.
