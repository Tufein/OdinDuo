package nl.retroid.touchguard;

import android.content.Context;
import android.provider.Settings;

final class BootIdentity {
    private BootIdentity() { }

    static String current(Context context) {
        try {
            int count = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            return count >= 0 ? "boot:" + count : "";
        } catch (RuntimeException exception) { return ""; }
    }
}
