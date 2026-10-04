# OdinDuo

Keep your **Retroid Dual Screen touchscreen working through sleep and wake on the AYN Odin 3**.

OdinDuo applies a temporary USB power-management workaround through the stock AYN service. Its English Material 3 dashboard follows your wallpaper colours and offers system, light and dark themes. Enable **Auto protect** once to keep it ready for every display connection.

![OdinDuo 1.0 dashboard in dark mode, shown in an Android 15 emulator](docs/screenshots/landscape-dark.png)

## Download

Install [OdinDuo 1.0.0](https://github.com/Tufein/OdinDuo/releases/tag/v1.0.0) from the release assets. The signed release APK can update the earlier OdinDuo installation in place; it keeps the same package and signing certificate. A SHA-256 checksum is included. The 1.0 APK is **not debuggable**. Private signing keys are never published.

## Use

1. Open **OdinDuo** and turn on **Auto protect**, or tap **Start protection** for a single session.
2. Connect the Retroid Dual Screen and wait for **Protected**.
3. Return to your launcher. Protection continues when you swipe OdinDuo out of recent apps and during sleep.
4. Unplug the display: its original USB power settings return. With Auto protect enabled, OdinDuo stays ready and protects the next connection automatically.

**Auto protect** is off by default. Enabling it starts a visible foreground service that waits for the display, including while the app is closed. It requests restoration of this opted-in mode after reboot and app updates. No USB power settings are changed while waiting. Turn Auto protect off to keep only the current active session; **Stop protection** or the notification’s **Stop** action ends protection and switches Auto protect off too.

Android may ask for notification permission. Allow it to see background status and the Stop action. Android’s **Force stop** or **Active apps → Stop** can still end the app; reopen OdinDuo to resume. Its own Stop button confirms immediate restoration; after unexpected process loss the helper allows a bounded restart grace before restoring.

**Appearance** lets you choose system, light or dark mode. **Help & diagnostics → Share diagnostics** exports only the app’s bounded logs and basic device/version information through Android’s share sheet. Nothing is sent automatically; the app has no internet permission.

**Compatibility:** tested on an AYN Odin 3 running Android 15, firmware `Odin3_V1.0.0.187_20260616_193307_user`, with the Retroid touchscreen `222a:0001`. Other devices, firmware versions and USB topologies are unverified. Android 13 or newer is required, but the stock AYN service and the expected Odin 3 USB topology must also be present.

Keeping USB awake may use extra battery, including during sleep. Battery impact has not been measured. This prevents the observed failure during an active session; it is not a guaranteed recovery method for a touchscreen that has already stopped responding.

## How it works

During investigation, Android restored the display and input mapping after wake, while the Retroid USB device and host remained runtime-suspended and stopped delivering raw touch events. Preventing runtime autosuspend on the Retroid USB chain kept touch working in the successful physical test.

OdinDuo uses the existing privileged **AYN `PServerBinder` service** to run its bundled shell guard as UID 0. Installing Magisk, unlocking the bootloader or flashing firmware is not needed on the tested stock Odin. This is privileged execution through a vendor service; firmware changes can affect availability.

The guard:

- Matches only USB vendor/product `222a:0001` and the actual ancestor chain under `/sys/devices/platform/soc/a600000.ssusb`.
- Saves original `power/control` values and device identities before setting them to `on`; verifies writes and reapplies them if firmware overwrites them.
- Restores saved settings in reverse order on Stop or detach. Closing the UI does not end the session. It skips removed or replaced USB devices and values changed independently.
- Ties the session to the foreground service’s PID, process start time, UID, package name and a unique marker. The service runs in its own `:guard` process and uses Android’s `START_STICKY` restart policy. Atomic status snapshots let the reopened UI display the existing session without starting a duplicate guard.
- Allows up to 60 seconds for Android to restart an unexpectedly killed service process; the restarted service adopts the existing guard and keeps the saved USB settings. If no valid owner returns, the guard restores settings and exits. Explicit Stop bypasses this grace period. A separate watchdog handles unexpected worker termination.
- Retains restoration snapshots if a write fails rather than silently discarding them.

The global `usbcore.autosuspend` setting is untouched. Runtime USB suspension and system sleep are different mechanisms. The app does not request an Android CPU wake lock, inject touches, alter launcher configuration, or request internet access.

**Automatic connection handling:** the running guard detects the Retroid device directly in the USB topology, restores each finished connection before preparing the next, and keeps its foreground notification visible. It does not rely on a cold USB attach broadcast: Android can filter this accessory’s boot-HID interface. Open the app and enable Auto protect once; Android force-stop and firmware background restrictions can require reopening it.

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

The app remains `nl.retroid.touchguard` so OdinDuo can update the original installation. Version **1.0.0** adds automatic connection sessions, opted-in boot/update startup, a redesigned dashboard, theme selection, adaptive/themed launcher icons and diagnostics sharing. The device-scoped shell power guard is unchanged.

The canonical guard in `tools/pserver-power-guard.sh` is bundled as a generated resource during the build. Generated resources, APKs, device captures and signing keys are excluded from Git. `./app/build.sh` produces a debug APK for development. To build the non-debuggable release:

```sh
./app/build.sh release
```

Release signing uses the existing ignored `app/build/debug.keystore` for compatibility with published versions. For your own signing key, set `ODINDUO_KEYSTORE`, `ODINDUO_STORE_PASSWORD`, `ODINDUO_KEY_ALIAS` and `ODINDUO_KEY_PASSWORD` in your local environment. Release builds require a private key; do not commit or publish it. A locally generated debug key cannot reproduce the public release’s signature.

For an existing installation, preserve its signing key when building updates. The project reuses `app/build/debug.keystore` when present; otherwise Gradle uses its normal local debug key. APKs built with a different key cannot update an existing installation in place. Stop protection before uninstalling or replacing an installation.

## Checks

```sh
./tools/check.sh
./gradlew :app:lintRelease
```

The checks verify process identity parsing, rejection of truncated records, shell argument quoting without command substitution, USB descriptor/target validation, and log parsing that honours the latest power state and gives restoration failures precedence. The shell integration test runs the real guard against temporary fake proc/USB files: process loss preserves power settings, a reused PID is rejected, a new service adopts the session without resetting USB, explicit Stop restores promptly, and an expired restart grace restores the original settings. It never writes host or connected-device power settings. Emulator checks cannot establish that physical Retroid touch survives sleep.

Physical validation of the workaround recorded three sleep/wake pairs with genuine raw touch events after each wake. The tester reported five uninterrupted cycles in that run. The USB host was active in all 69 recorded samples; the Retroid device was active in 68 samples and briefly suspended in one. The guard corrected six firmware overwrites of the touchscreen's power setting.

The self-contained 0.3 app was subsequently confirmed working by the tester. Start, Stop, launcher/background use and forced app termination were also checked on the Odin. Version 0.5.0 lifecycle checks passed on an isolated Android 15 emulator using a test-only stand-in for the stock PServer service: swiping the app away kept the foreground service running; killing only the service process triggered a sticky restart that adopted the same guard; reopening the UI preserved the session; Stop reported completion only after the guard exited. Cleanup of a leftover 0.4 session was also checked. These emulator lifecycle checks used a service stand-in and cannot replace physical touch testing.

Version 0.4.0 was installed as an in-place update on the same Odin. Its English UI was checked in portrait and landscape, in light and dark mode. Start reached the waiting state; Stop ended the worker and watchdog with the USB controllers still at their original `auto` settings while no RDS was connected. These results cover the tested device and firmware; long-duration reliability and battery use remain unmeasured.

Version 1.0 validation additionally exercised the signed, non-debuggable APK in an isolated Android 15 emulator with a test-only PServer adapter and temporary fake USB files. Three automatic detach/restore/reconnect cycles passed. Closing recents retained protection; killing the service adopted the same existing guard. App replacement resumed automatic mode. A boot-completed receiver invocation and an immediate stop/restart restoration check passed; a complete physical Odin reboot remains unverified. Both themes and orientations were inspected. These simulated USB checks establish lifecycle and restoration behaviour, not physical touch or battery performance. The Odin was not connected during the 1.0 release checks.

## Diagnostics and removal

For the published 1.0 APK, use **Help & diagnostics → Share diagnostics**. `adb shell run-as` is intentionally unavailable in the non-debuggable release. Diagnostics omit device serial numbers, account information and installed-app lists; session tokens are redacted.

For developer builds, use the Odin’s explicit ADB serial, especially if other devices or emulators are connected:

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

For the separate service process, current UI state is stored in `files/guard-status.json`; session ownership is in `files/guard-owner` and `files/guard-heartbeat`.

The diagnostic logger and previous experimental console helpers remain in `tools/`. USB-port reset, a display cycle and USB data off/on were investigated but did not establish a reliable fix. They are not part of OdinDuo's normal operation.

## Credits and references

- [Crypt0Shmipt0's Odin 3 RDS USB-pinning approach](https://github.com/Crypt0Shmipt0/odin3-rds-touch-fix/blob/48a49f5ca4417682b0a931157947e63579e61a40/scripts/rds_usbpin.sh) informed the power-management workaround. OdinDuo adds chain scoping, original-state restoration and session ownership.
- [AurelioB's ClusterTune PServer implementation](https://github.com/AurelioB/ClusterTune/blob/4920c5a82925772f5765dbee1f4679242a72cada/app/src/main/java/com/aure/clustertune/root/RootExec.kt), which credits **FeralAI's O2P Tweaks**, documents the stock Binder wire format used by OdinDuo's independent adapter.
- [Linux 6.6 USB power management](https://docs.kernel.org/6.6/driver-api/usb/power-management.html) explains runtime autosuspend and `power/control`.
- [AOSP UsbHostManager](https://android.googlesource.com/platform/frameworks/base/+/master/services/usb/java/com/android/server/usb/UsbHostManager.java) documents boot-HID filtering.
- [Android service restart and task-removal behaviour](https://developer.android.com/reference/android/app/Service#START_STICKY) and [Android user-initiated stopping](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping) explain the lifecycle used in 0.5.0.
- [Android special-use foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use) and [dynamic colours](https://developer.android.com/develop/ui/views/theming/dynamic-colors) describe the Android APIs used by the app.
- [Material Components for Android](https://github.com/material-components/material-components-android/releases/tag/1.14.0) supplies the Material 3 interface.

Independent community project; not affiliated with AYN or Retroid. Original OdinDuo source is available under the [MIT licence](LICENSE). External projects and bundled dependencies retain their respective licences; see [NOTICE.md](NOTICE.md).
