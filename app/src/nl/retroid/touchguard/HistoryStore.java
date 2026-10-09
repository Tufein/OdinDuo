package nl.retroid.touchguard;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/** Small, bounded local timeline used by the dashboard and support export. */
final class HistoryStore {
    private static final int MAX_ENTRIES = 80;
    private static final long MAX_BYTES = 256 * 1024;
    private static final int MAX_EVENT_LENGTH = 1024;
    private static final String FILE_NAME = "session-history.json";

    private HistoryStore() {}

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    static synchronized void record(Context context, String event) {
        try {
            locked(context, () -> {
                JSONArray entries = read(context);
                String safe = event == null ? "" : event.replaceAll("(?i)\\b[a-f0-9]{32}\\b", "[session]");
                if (safe.length() > MAX_EVENT_LENGTH) safe = safe.substring(0, MAX_EVENT_LENGTH) + "…";
                entries.put(new JSONObject().put("at", System.currentTimeMillis()).put("event", safe));
                while (entries.length() > MAX_ENTRIES) entries.remove(0);
                write(context, entries);
                return null;
            });
        } catch (Exception exception) {
            Log.w("RetroidTouchGuard", "Cannot save session history", exception);
        }
    }

    static synchronized String display(Context context) {
        try {
            JSONArray entries = locked(context, () -> read(context));
            if (entries.length() == 0) return context.getString(R.string.history_empty);
            StringBuilder result = new StringBuilder();
            for (int index = entries.length() - 1; index >= 0; index--) {
                JSONObject entry = entries.getJSONObject(index);
                long at = entry.optLong("at", 0L);
                if (result.length() > 0) result.append("\n\n");
                result.append(DateUtils.formatDateTime(context, at,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME))
                        .append("\n").append(entry.optString("event", ""));
            }
            return result.toString();
        } catch (Exception exception) {
            return context.getString(R.string.history_unavailable);
        }
    }

    static synchronized String latest(Context context) {
        try {
            JSONArray entries = locked(context, () -> read(context));
            if (entries.length() == 0) return context.getString(R.string.history_no_recent_event);
            return entries.getJSONObject(entries.length() - 1).optString("event",
                    context.getString(R.string.history_no_recent_event));
        } catch (Exception exception) {
            return context.getString(R.string.history_unavailable);
        }
    }

    static synchronized String export(Context context) {
        try { return locked(context, () -> read(context).toString()); }
        catch (Exception exception) { return "[]"; }
    }

    static synchronized boolean clear(Context context) {
        try { locked(context, () -> { write(context, new JSONArray()); return null; }); return true; }
        catch (Exception exception) {
            Log.w("RetroidTouchGuard", "Cannot clear session history", exception);
            return false;
        }
    }

    private static JSONArray read(Context context) throws Exception {
        File target = file(context).getBaseFile();
        File backup = new File(target.getPath() + ".bak");
        if (!target.isFile() && !backup.isFile()) return new JSONArray();
        if (target.length() > MAX_BYTES || backup.length() > MAX_BYTES)
            throw new IllegalStateException("History exceeds its size limit");
        JSONArray entries = new JSONArray(new String(file(context).readFully(), StandardCharsets.UTF_8));
        while (entries.length() > MAX_ENTRIES) entries.remove(0);
        return entries;
    }

    /** The activity and guard use separate processes, so Java monitors alone cannot serialize writes. */
    private static <T> T locked(Context context, Callable<T> operation) throws Exception {
        File lockFile = new File(context.getFilesDir(), FILE_NAME + ".lock");
        try (FileOutputStream output = new FileOutputStream(lockFile, true);
                FileChannel channel = output.getChannel()) {
            long deadline = android.os.SystemClock.uptimeMillis() + 100;
            do {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    try (FileLock held = lock) { return operation.call(); }
                }
                Thread.sleep(10);
            } while (android.os.SystemClock.uptimeMillis() < deadline);
            throw new IllegalStateException("History is busy");
        }
    }

    private static void write(Context context, JSONArray entries) throws Exception {
        byte[] encoded = entries.toString().getBytes(StandardCharsets.UTF_8);
        // Escaped control characters and older long events can exceed the per-event budget.
        while (encoded.length > MAX_BYTES && entries.length() > 0) {
            entries.remove(0);
            encoded = entries.toString().getBytes(StandardCharsets.UTF_8);
        }
        AtomicFile target = file(context);
        FileOutputStream output = null;
        try {
            output = target.startWrite();
            output.write(encoded);
            target.finishWrite(output);
        } catch (Exception exception) {
            if (output != null) target.failWrite(output);
            throw exception;
        }
    }
}
