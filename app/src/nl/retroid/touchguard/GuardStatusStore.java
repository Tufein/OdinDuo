package nl.retroid.touchguard;

import android.content.Context;
import android.os.Process;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Atomic status snapshots shared by the UI and the separate foreground-service process. */
final class GuardStatusStore {
    static final class Status {
        final boolean running;
        final String state;
        final String text;
        final long changedAt;
        final long sessionStartedAt;
        Status(boolean running, String state, String text, long changedAt, long sessionStartedAt) {
            this.running = running;
            this.state = state;
            this.text = text;
            this.changedAt = changedAt;
            this.sessionStartedAt = sessionStartedAt;
        }
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "guard-status.json"));
    }

    static void write(Context context, boolean running, String state, String text) {
        AtomicFile file = file(context);
        FileOutputStream output = null;
        try {
            String stat = new String(Files.readAllBytes(new File("/proc/self/stat").toPath()), StandardCharsets.UTF_8);
            long now = System.currentTimeMillis();
            long sessionStartedAt = now;
            try {
                JSONObject previous = new JSONObject(new String(file(context).readFully(), StandardCharsets.UTF_8));
                long previousStart = previous.optLong("sessionStartedAt", 0L);
                if (previousStart > 0L && previous.optBoolean("running")) sessionStartedAt = previousStart;
            } catch (Exception ignored) { }
            JSONObject value = new JSONObject().put("running", running).put("state", state).put("status", text)
                    .put("changedAt", now).put("sessionStartedAt", sessionStartedAt)
                    .put("pid", Process.myPid()).put("start", ProcessIdentity.startTicks(stat));
            output = file.startWrite();
            output.write(value.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            ProtectionTileService.refresh(context);
        } catch (Exception exception) {
            if (output != null) file.failWrite(output);
            EventLog.write(context, "status snapshot failed " + exception);
        }
    }

    static Status read(Context context) {
        try {
            JSONObject value = new JSONObject(new String(file(context).readFully(), StandardCharsets.UTF_8));
            boolean running = value.optBoolean("running") && (liveProcess(value) || VendorBridge.guardAlive(context));
            String state = value.optString("state", "idle");
            String text = value.optString("status", context.getString(R.string.status_idle));
            long changedAt = value.optLong("changedAt", 0L);
            long sessionStartedAt = value.optLong("sessionStartedAt", changedAt);
            if (!running && value.optBoolean("running")) {
                return new Status(false, "idle", context.getString(R.string.status_restarted), changedAt, sessionStartedAt);
            }
            return new Status(running, state, text, changedAt, sessionStartedAt);
        } catch (Exception exception) {
            return new Status(false, "idle", context.getString(R.string.status_idle), 0L, 0L);
        }
    }

    private static boolean liveProcess(JSONObject value) {
        try {
            int pid = value.getInt("pid");
            if (pid <= 0) return false;
            String stat = new String(Files.readAllBytes(new File("/proc/" + pid + "/stat").toPath()), StandardCharsets.UTF_8);
            String name = new String(Files.readAllBytes(new File("/proc/" + pid + "/cmdline").toPath()), StandardCharsets.UTF_8);
            return name.startsWith("nl.retroid.touchguard:guard" + (char) 0)
                    && value.getString("start").equals(ProcessIdentity.startTicks(stat));
        } catch (Exception exception) { return false; }
    }
}
