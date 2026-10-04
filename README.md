# OdinDuo

Keep your **Retroid Dual Screen touchscreen working through sleep and wake on the AYN Odin 3**.

OdinDuo applies a temporary USB power-management workaround through the stock AYN service. Its English interface uses Material 3, wallpaper-based system colours where supported, and automatic light/dark mode.

![OdinDuo on the Odin 3, using the system Material You palette](docs/screenshots/landscape-dark.png)

## Use

1. Open **OdinDuo** and tap **Start protection**.
2. Connect the Retroid Dual Screen. Starting with your computer attached is fine: the app waits for the display.
3. Wait for **Touch stays connected / Protected**, then return to your launcher.
4. Use **Stop protection**, the notification's **Stop** action, or unplug the display to end the session and restore power settings.

Start a new session after rebooting or reconnecting the display. There is no automatic start at boot. Android may show a notification permission prompt so the background session remains visible.

**Compatibility:** tested on an AYN Odin 3 running Android 15, firmware `Odin3_V1.0.0.187_20260616_193307_user`, with the Retroid touchscreen `222a:0001`. Other devices, firmware versions and USB topologies are unverified. Android 13 or newer is required, but the stock AYN service and the expected Odin 3 USB topology must also be present.

Keeping USB awake may use extra battery, including during sleep. Battery impact has not been measured. This prevents the observed failure during an active session; it is not a guaranteed recovery method for a touchscreen that has already stopped responding.

## How it works

During investigation, Android restored the display and input mapping after wake, while the Retroid USB device and host remained runtime-suspended and stopped delivering raw touch events. Preventing runtime autosuspend on the Retroid USB chain kept touch working in the successful physical test.

OdinDuo uses the existing privileged **AYN `PServerBinder` service** to run its bundled shell guard as UID 0. Installing Magisk, unlocking the bootloader or flashing firmware is not needed on the tested stock Odin. This is privileged execution through a vendor service; firmware changes can affect availability.

The guard:

- Matches only USB vendor/product `222a:0001` and the actual ancestor chain under `/sys/devices/platform/soc/a600000.ssusb`.
- Saves original `power/control` values and device identities before setting them to `on`; verifies writes and reapplies them if firmware overwrites them.
- Restores saved settings in reverse order on Stop, detach, or app-process death. It skips removed or replaced USB devices and values changed independently.
- Ties the session to PID, process start time, UID, package name and a unique marker. A separate watchdog handles unexpected worker termination.
- Retains restoration snapshots if a write fails rather than silently discarding them.

The global `usbcore.autosuspend` setting is untouched. Runtime USB suspension and system sleep are different mechanisms. The app does not request an Android CPU wake lock, inject touches, alter launcher configuration, or request internet access.

Normal Android USB permission is insufficient for this accessory: its boot-HID interface is filtered from the host API on the tested device. This is why OdinDuo uses the vendor service instead of a USB permission dialog.

## Build and install

Requirements: **JDK 17 or newer**, Android SDK **platform 36**, build-tools **36.0.0**, and platform-tools for ADB installation. The Gradle 9.3.1 wrapper downloads the build tool automatically; Android Gradle Plugin is pinned to 9.1.0 and Material Components to 1.14.0.

Configure `JAVA_HOME` and `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) for your installations. `app/build.sh` also recognises the usual macOS SDK and Homebrew OpenJDK 21 locations.

```sh
./app/build.sh
adb devices -l
adb -s YOUR_ODIN_SERIAL install -r app/build/OdinDuo.apk
adb -s YOUR_ODIN_SERIAL shell am start -n nl.retroid.touchguard/.MainActivity
```

The app remains `nl.retroid.touchguard` so OdinDuo can update the original test installation. Version **0.4.0** changes the name, English copy and Material You interface while retaining the working power guard.

The canonical guard in `tools/pserver-power-guard.sh` is bundled as a generated resource during the build. Generated resources, APKs, device captures and signing keys are excluded from Git. This build produces a debuggable APK for direct installation. There is no published production signing key.

For an existing installation, preserve its signing key when building updates. The project reuses `app/build/debug.keystore` when present; otherwise Gradle uses its normal local debug key. APKs built with a different key cannot update an existing installation in place. Stop protection before uninstalling or replacing an installation.

## Checks

```sh
./tools/check.sh
./gradlew :app:lintDebug
```

The existing checks verify process identity parsing, rejection of truncated records, shell argument quoting without command substitution, and USB descriptor/target validation. Emulator checks cannot establish that physical Retroid touch survives sleep.

Physical validation of the workaround recorded three sleep/wake pairs with genuine raw touch events after each wake. The tester reported five uninterrupted cycles in that run. The USB host was active in all 69 recorded samples; the Retroid device was active in 68 samples and briefly suspended in one. The guard corrected six firmware overwrites of the touchscreen's power setting.

The self-contained 0.3 app was subsequently confirmed working by the tester. Start, Stop, launcher/background use and forced app termination were also checked on the Odin. Version 0.4.0 was installed as an in-place update on the same Odin. Its English UI was checked in portrait and landscape, in light and dark mode. Start reached the waiting state; Stop ended the worker and watchdog with the USB controllers still at their original `auto` settings while no RDS was connected. These results cover the tested device and firmware; long-duration reliability and battery use remain unmeasured.

## Diagnostics and removal

Use the Odin's explicit ADB serial, especially if other devices or emulators are connected:

```sh
python3 tools/collect-usb-open-test.py --serial YOUR_ODIN_SERIAL --kind app
./capture.sh YOUR_ODIN_SERIAL before-sleep
adb -s YOUR_ODIN_SERIAL shell run-as nl.retroid.touchguard cat files/power-guard.txt
adb -s YOUR_ODIN_SERIAL shell run-as nl.retroid.touchguard cat files/guard-events.txt
```

Collectors have bounded waits and save output in the ignored `capture/` directory. Captures can include device identifiers and installed-app information; review them before sharing. App events rotate at 512 KiB and polling reads at most the latest 32 KiB of the power log.

If restoration reports a failure, stop testing and collect logs before starting another session. Snapshots remain in `/data/local/tmp/retroid-power-guard-state`. Do not delete that directory to bypass an unresolved restoration.

To remove OdinDuo, tap **Stop protection**, wait for restoration, then:

```sh
adb -s YOUR_ODIN_SERIAL uninstall nl.retroid.touchguard
```

The diagnostic logger and previous experimental console helpers remain in `tools/`. USB-port reset, a display cycle and USB data off/on were investigated but did not establish a reliable fix. They are not part of OdinDuo's normal operation.

## Credits and references

- [Crypt0Shmipt0's Odin 3 RDS USB-pinning approach](https://github.com/Crypt0Shmipt0/odin3-rds-touch-fix/blob/48a49f5ca4417682b0a931157947e63579e61a40/scripts/rds_usbpin.sh) informed the power-management workaround. OdinDuo adds chain scoping, original-state restoration and session ownership.
- [AurelioB's ClusterTune PServer implementation](https://github.com/AurelioB/ClusterTune/blob/4920c5a82925772f5765dbee1f4679242a72cada/app/src/main/java/com/aure/clustertune/root/RootExec.kt), which credits **FeralAI's O2P Tweaks**, documents the stock Binder wire format used by OdinDuo's independent adapter.
- [Linux 6.6 USB power management](https://docs.kernel.org/6.6/driver-api/usb/power-management.html) explains runtime autosuspend and `power/control`.
- [AOSP UsbHostManager](https://android.googlesource.com/platform/frameworks/base/+/master/services/usb/java/com/android/server/usb/UsbHostManager.java) documents boot-HID filtering.
- [Android special-use foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use) and [dynamic colours](https://developer.android.com/develop/ui/views/theming/dynamic-colors) describe the Android APIs used by the app.
- [Material Components for Android](https://github.com/material-components/material-components-android/releases/tag/1.14.0) supplies the Material 3 interface.

Independent community project; not affiliated with AYN or Retroid. Original OdinDuo source is available under the [MIT licence](LICENSE). External projects and bundled dependencies retain their respective licences; see [NOTICE.md](NOTICE.md).
