package nl.retroid.touchguard;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;

/** Read-only checks: no vendor commands, permissions or settings are changed. */
final class SetupReport {
    static String read(Context context) {
        GuardStatusStore.Status snapshot = GuardStatusStore.read(context);
        ActivityManager activity = context.getSystemService(ActivityManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        boolean primary = Process.myUid() < 100000;
        return context.getString(R.string.setup_device, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE)
                + "\n\n" + context.getString(R.string.setup_vendor,
                        context.getString(VendorBridge.available() ? R.string.setup_available : R.string.setup_unavailable))
                + "\n" + context.getString(primary ? R.string.setup_primary : R.string.setup_secondary)
                + "\n\n" + context.getString(R.string.setup_notifications,
                        context.getString(notifications.areNotificationsEnabled() ? R.string.setup_allowed : R.string.setup_off))
                + "\n" + context.getString(activity.isBackgroundRestricted() ? R.string.setup_background_restricted : R.string.setup_background_allowed)
                + "\n" + context.getString(power.isIgnoringBatteryOptimizations(context.getPackageName())
                        ? R.string.setup_battery_unrestricted : R.string.setup_battery_optimized)
                + "\n\n" + context.getString(R.string.setup_auto,
                        context.getString(GuardPreferences.automatic(context) ? R.string.setup_on : R.string.setup_off))
                + "\n" + snapshot.text
                + "\n\n" + context.getString(R.string.setup_guidance);
    }
}
