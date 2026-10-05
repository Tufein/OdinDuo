package nl.retroid.touchguard;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Only launches our bundled, device-scoped power guard through stock AYN PServer. */
final class VendorBridge {
    private VendorBridge() { }

    private static IBinder service() throws Exception {
        IBinder value = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "PServerBinder");
        if (value == null || !value.isBinderAlive()) {
            throw new IllegalStateException("The built-in AYN service is unavailable.");
        }
        return value;
    }

    static boolean available() {
        // The device-scoped shell helpers use the primary Android user's app directory.
        if (Process.myUid() >= 100000) return false;
        try { service(); return true; } catch (Exception exception) { return false; }
    }

    private static void request(String command) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            // Stock wire format identified in ClusterTune/O2P Tweaks; see README attribution.
            data.writeStringArray(new String[]{command, "0"});
            if (!service().transact(0, data, reply, 0)) {
                throw new IllegalStateException("The AYN service rejected the request.");
            }
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    static File logFile(Context context) { return new File(context.getFilesDir(), "power-guard.txt"); }

    static boolean guardAlive(Context context) {
        try {
            String token = new String(Files.readAllBytes(new File(context.getFilesDir(), "guard-enabled").toPath()), StandardCharsets.UTF_8).trim();
            if (!token.matches("[a-f0-9]{32}")) return false;
            String[] heartbeat = new String(Files.readAllBytes(new File(context.getFilesDir(), "guard-heartbeat").toPath()), StandardCharsets.UTF_8).trim().split("\\s+");
            if (heartbeat.length != 2 || !token.equals(heartbeat[0])) return false;
            long age = SystemClock.elapsedRealtime() / 1000 - Long.parseLong(heartbeat[1]);
            return age >= 0 && age <= 10;
        } catch (Exception exception) { return false; }
    }

    private static void writeOwner(Context context, String token) throws Exception {
        String stat = new String(Files.readAllBytes(new File("/proc/self/stat").toPath()), StandardCharsets.UTF_8);
        String identity = Process.myPid() + "\n" + ProcessIdentity.startTicks(stat) + "\n" + Process.myUid() + "\n" + token + "\n";
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), "guard-owner"));
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(identity.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception exception) {
            if (output != null) file.failWrite(output);
            throw exception;
        }
    }

    static void start(Context context) throws Exception {
        if (Process.myUid() >= 100000) throw new IllegalStateException("Use OdinDuo in the primary Android user profile.");
        if (guardAlive(context)) {
            String token = new String(Files.readAllBytes(new File(context.getFilesDir(), "guard-enabled").toPath()), StandardCharsets.UTF_8).trim();
            writeOwner(context, token);
            EventLog.write(context, "existing power session adopted; pid=" + Process.myPid());
            return;
        }
        // Clear an obsolete session before starting; do not overwrite a running worker's script or log.
        if (new File(context.getFilesDir(), "guard-enabled").exists()) stop(context);
        File script = new File(context.getFilesDir(), "power-guard.sh");
        try (InputStream input = context.getResources().openRawResource(R.raw.power_guard);
             OutputStream output = context.openFileOutput(script.getName(), Context.MODE_PRIVATE)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
        try (OutputStream ignored = context.openFileOutput("power-guard.txt", Context.MODE_PRIVATE)) { }
        try (OutputStream ignored = context.openFileOutput("guard-heartbeat", Context.MODE_PRIVATE)) { }
        String stat = new String(Files.readAllBytes(new File("/proc/self/stat").toPath()), StandardCharsets.UTF_8);
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        try (OutputStream marker = context.openFileOutput("guard-enabled", Context.MODE_PRIVATE)) {
            marker.write(token.getBytes(StandardCharsets.UTF_8));
        }
        writeOwner(context, token);
        String command = "/system/bin/sh " + ProcessIdentity.quote(script.getAbsolutePath())
                + " --watch-app " + Process.myPid() + " " + ProcessIdentity.startTicks(stat)
                + " " + Process.myUid() + " " + token + " > " + ProcessIdentity.quote(logFile(context).getAbsolutePath())
                + " 2>&1 &";
        request(command);
        EventLog.write(context, "vendor start accepted; pid=" + Process.myPid());
    }

    static void stop(Context context) throws Exception {
        // Helpers address user 0. An unsupported profile must not stop that user's session,
        // even if an older installation left a copied script in this profile's directory.
        if (Process.myUid() >= 100000) return;
        // The daemon also watches this marker: stopping remains effective if Binder is unavailable.
        context.deleteFile("guard-enabled");
        new AtomicFile(new File(context.getFilesDir(), "guard-owner")).delete();
        context.deleteFile("guard-heartbeat");
        File script = new File(context.getFilesDir(), "power-guard.sh");
        if (!script.isFile()) return;
        // The tested stock PServer accepts the long compound command but does not execute it.
        // A short request to a bundled helper also works with a guard from an earlier APK.
        File stopScript = new File(context.getFilesDir(), "power-stop.sh");
        try (InputStream input = context.getResources().openRawResource(R.raw.power_stop);
             OutputStream output = context.openFileOutput(stopScript.getName(), Context.MODE_PRIVATE)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        String acknowledgement = "STOP acknowledged " + token;
        request("/system/bin/sh " + ProcessIdentity.quote(stopScript.getAbsolutePath()) + " " + token);
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < deadline) {
            String log = readLog(context);
            if (log.contains(acknowledgement + " result=0")) {
                EventLog.write(context, "vendor stop completed");
                return;
            }
            if (log.contains(acknowledgement + " result=")) {
                throw new IllegalStateException("USB power restoration failed.");
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("USB power restoration was not confirmed.");
    }

    static String readLog(Context context) throws Exception {
        File file = logFile(context);
        if (!file.isFile()) return "";
        // Only read our own log. Limit polling costs if a firmware repeatedly overwrites PM.
        try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(file, "r")) {
            long length = input.length();
            input.seek(Math.max(0, length - 32768));
            byte[] bytes = new byte[(int) Math.min(length, 32768)];
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
