package nl.retroid.touchguard;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

/** Store stable resource names, so snapshots survive both app updates and language changes. */
final class StatusText {
    private static final int[] RESOURCES = {
        R.string.status_idle, R.string.status_restarted, R.string.status_stopped,
        R.string.status_guard_error, R.string.status_detached, R.string.status_active,
        R.string.status_detected, R.string.status_waiting, R.string.status_auto_waiting,
        R.string.status_launch_timeout, R.string.status_log_error, R.string.status_preparing,
        R.string.status_fgs_error, R.string.status_starting, R.string.status_start_error,
        R.string.status_stopping, R.string.status_stop_error, R.string.status_settings_error,
        R.string.status_recovering, R.string.status_helper_lost, R.string.status_vendor_unavailable
    };

    private StatusText() { }

    static String read(Context context, JSONObject value) {
        context = AppLanguage.current(context);
        String name = value.optString("statusResource");
        for (int resource : RESOURCES) {
            if (context.getResources().getResourceEntryName(resource).equals(name)) {
                JSONArray parameters = value.optJSONArray("statusArguments");
                Object[] args = new Object[parameters == null ? 0 : parameters.length()];
                for (int index = 0; index < args.length; index++) args[index] = parameters.optString(index);
                try { return context.getString(resource, args); }
                catch (RuntimeException ignored) { break; }
            }
        }
        return value.optString("status", context.getString(R.string.status_idle));
    }
}
