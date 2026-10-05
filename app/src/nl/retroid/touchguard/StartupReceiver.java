package nl.retroid.touchguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restore only the automatic mode the user has explicitly enabled. */
public final class StartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        ProtectionTileService.refresh(context);
        if (!GuardPreferences.automatic(context)) return;
        try {
            context.startForegroundService(new Intent(context, GuardService.class));
        } catch (RuntimeException exception) {
            EventLog.write(context, "automatic startup deferred " + exception);
        }
    }
}
