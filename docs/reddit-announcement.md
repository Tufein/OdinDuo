# Fix for Retroid Dual Screen touch dropping after sleep on the AYN Odin 3 — OdinDuo

If your Retroid Dual Screen still shows an image but touch stops working after sleep or switching apps, I had the same problem on my Odin 3.

I made **OdinDuo**, a free, open-source app that keeps the Retroid touchscreen’s USB connection awake while protection is running. This USB power-management workaround solved the touch dropouts on my setup. It keeps running when you go back to your launcher or swipe the app out of recent apps, and restores the original USB power settings when you unplug the screen or stop protection.

**How to use the fix**

1. Download and install the APK.
2. Open OdinDuo and enable **Auto protect**, or tap **Start protection** for one session.
3. Connect your Retroid Dual Screen and wait for **Protected**.
4. Go back to your game. Auto protect stays ready when you unplug and reconnect the screen.

**Stable download:** [OdinDuo 1.1.0](https://github.com/Tufein/OdinDuo/releases/tag/v1.1.0)

Thanks for all the feedback and support so far. **1.1.0** brings these fixes and additions:

- Checks whether the background helper is still responding, so an old successful reading cannot leave the app showing “Protected”. Auto protect can recover after confirmed USB restoration, with a limit on repeated attempts.
- Verifies the original power settings after restoring them and keeps the recovery data if restoration fails.
- Adds an Android Quick Settings tile to turn Auto protect on or stop protection without opening the app.
- Adds **Check setup** for the AYN service, notifications and Android background restrictions. These checks are included in diagnostics too.
- Keeps a healthy session running even when repeated firmware activity fills the log.
- Adds a failure notification with **Retry protection**. A retry confirms the previous USB restoration before starting a fresh session.
- Keeps the Quick Settings tile up to date when protection changes while the panel is closed.
- Avoids asking for notification permission again when you change the theme.

**Updating:** install 1.1.0 over your existing OdinDuo installation. Stop protection once and re-enable Auto protect to load the updated helper. The release includes an APK checksum.

The workaround was tested on **AYN Odin 3, Android 15, firmware 1.0.0.187**. It uses the built-in AYN service: no Magisk, bootloader unlock or firmware flash needed on that setup. Other devices and firmware versions are unverified. Start protection while touch still works; it is not a guaranteed way to recover touch after it has already failed. Keeping USB awake may use extra battery, which I haven’t measured yet.

The app has an English Material You interface and no internet permission. Brightness matching is manual using the Retroid screen’s buttons; automatic brightness sync is unavailable on my tested setup.

**Source / report a problem:** [Tufein/OdinDuo](https://github.com/Tufein/OdinDuo). Please include your model, firmware, app version and what happened. You can export logs through **Help & diagnostics → Share diagnostics**.

Credit to Crypt0Shmipt0 for the USB-pinning approach, and AurelioB’s ClusterTune / FeralAI’s O2P Tweaks for documenting the stock AYN service. This is an independent community project, not an official AYN or Retroid app.
