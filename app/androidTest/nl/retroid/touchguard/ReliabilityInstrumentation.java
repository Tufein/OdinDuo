package nl.retroid.touchguard;

import android.app.Instrumentation;
import android.app.LocaleManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Run only on the isolated emulator; this suite changes the test installation's local state. */
public final class ReliabilityInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        LocaleList locales = manager.getApplicationLocales();
        boolean ownsTestState = false;
        try {
            require(android.os.Build.HARDWARE.equals("ranchu") || android.os.Build.HARDWARE.equals("goldfish"),
                    "Run this suite only on a disposable Android emulator");
            require(!GuardStatusStore.read(context).running, "Stop emulator protection before instrumentation");
            ownsTestState = true;
            history(context);
            status(context, manager);
            result.putString("stream", "History bounds, cross-process writes, corruption, lock contention, token redaction, boot fencing and English/Dutch status checks passed.\n");
            result.putString("passed", "true");
        } catch (Throwable failure) {
            result.putString("stream", "FAILED: " + failure + "\n");
            result.putString("passed", "false");
        } finally {
            manager.setApplicationLocales(locales);
            if (ownsTestState) {
                HistoryStore.clear(context);
                GuardStatusStore.write(context, false, "idle", R.string.status_stopped);
            }
        }
        finish("true".equals(result.getString("passed")) ? -1 : 0, result);
    }

    private void history(Context context) throws Exception {
        require(HistoryStore.clear(context), "Cannot clear test history");
        for (int index = 0; index < 100; index++) HistoryStore.record(context, "event " + index);
        JSONArray entries = new JSONArray(HistoryStore.export(context));
        require(entries.length() == 80, "History is not bounded at 80");
        require(entries.getJSONObject(0).getString("event").equals("event 20"), "Wrong history retained");
        HistoryStore.record(context, "a".repeat(32) + " " + "x".repeat(20000));
        String exported = HistoryStore.export(context);
        require(!exported.contains("a".repeat(32)), "Session token was not redacted");
        entries = new JSONArray(exported);
        require(entries.getJSONObject(entries.length() - 1).getString("event").length() <= 1025,
                "Oversized history event");
        require(HistoryStore.clear(context), "Clear failed");
        for (int index = 0; index < 80; index++) HistoryStore.record(context, "\u0001".repeat(1024));
        require(new File(context.getFilesDir(), "session-history.json").length() <= 256 * 1024,
                "Escaped events exceeded the history byte limit");
        require(!new JSONArray(HistoryStore.export(context)).isNull(0), "Bounded history cannot be read");
        require(HistoryStore.clear(context), "Clear escaped history failed");
        ProcessBuilder builder = new ProcessBuilder("/system/bin/app_process", "/system/bin",
                getClass().getName(), context.getFilesDir().getAbsolutePath(), "worker", "40");
        builder.environment().put("CLASSPATH", context.getApplicationInfo().sourceDir + ":"
                + getContext().getApplicationInfo().sourceDir);
        builder.redirectErrorStream(true);
        Process writer = builder.start();
        for (int index = 0; index < 40; index++) {
            HistoryStore.record(context, "ui " + index);
            new JSONArray(HistoryStore.export(context));
            Thread.sleep(5);
        }
        require(writer.waitFor(20, TimeUnit.SECONDS) && writer.exitValue() == 0,
                "Cross-process history writer failed");
        entries = new JSONArray(HistoryStore.export(context));
        require(entries.length() == 80, "Concurrent writers lost history events");
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "session-history.json.lock"), true);
                FileChannel channel = output.getChannel(); FileLock held = channel.lock()) {
            long began = SystemClock.uptimeMillis();
            require(!HistoryStore.clear(context), "Busy history falsely reported successful clearing");
            HistoryStore.record(context, "busy write");
            require(SystemClock.uptimeMillis() - began < 1000, "History lock blocked protection");
        }
        File history = new File(context.getFilesDir(), "session-history.json");
        Files.write(history.toPath(), "broken JSON".getBytes(StandardCharsets.UTF_8));
        require(HistoryStore.display(context).equals(context.getString(R.string.history_unavailable)),
                "Corruption not surfaced");
        HistoryStore.record(context, "must not overwrite unreadable history");
        require(new String(Files.readAllBytes(history.toPath()), StandardCharsets.UTF_8).equals("broken JSON"),
                "Corrupt history was silently overwritten");
        require(HistoryStore.clear(context), "Cannot recover corrupt history with explicit Clear");
    }

    private void status(Context context, LocaleManager manager) throws Exception {
        manager.setApplicationLocales(LocaleList.forLanguageTags("en"));
        GuardStatusStore.write(context, false, "idle", R.string.status_stopped);
        File file = new File(context.getFilesDir(), "guard-status.json");
        JSONObject value = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        require(!value.getString("boot").isEmpty(), "No boot identity");
        require(value.getLong("sessionStartedElapsed") <= SystemClock.elapsedRealtime(), "Invalid elapsed start");
        require(GuardStatusStore.read(context).text.startsWith("Protection stopped"), "English status incorrect");
        manager.setApplicationLocales(LocaleList.forLanguageTags("nl"));
        require(GuardStatusStore.read(context).text.startsWith("Bescherming gestopt"), "Persisted status did not translate");
        value.put("boot", "boot:99999999").put("running", true).put("state", "active");
        Files.write(file.toPath(), value.toString().getBytes(StandardCharsets.UTF_8));
        GuardStatusStore.Status stale = GuardStatusStore.read(context);
        require(!stale.running && stale.state.equals("idle"), "Old boot reported running");
        GuardStatusStore.write(context, false, "idle", R.string.status_start_error, "test failure");
        require(GuardStatusStore.read(context).text.contains("test failure"), "Formatted error details lost");
    }

    public static void main(String[] args) throws Exception {
        Context context = new ContextWrapper(null) {
            @Override public File getFilesDir() { return new File(args[0]); }
        };
        for (int index = 0; index < Integer.parseInt(args[2]); index++) {
            HistoryStore.record(context, args[1] + " " + index);
            Thread.sleep(5);
        }
    }
}
