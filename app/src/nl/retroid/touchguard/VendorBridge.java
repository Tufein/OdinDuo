package nl.retroid.touchguard;

import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import java.io.File;
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

    static void start(Context context) throws Exception {
        File script = new File(context.getFilesDir(), "power-guard.sh");
        try (InputStream input = context.getResources().openRawResource(R.raw.power_guard);
             OutputStream output = context.openFileOutput(script.getName(), Context.MODE_PRIVATE)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
        try (OutputStream ignored = context.openFileOutput("power-guard.txt", Context.MODE_PRIVATE)) { }
        String stat = new String(Files.readAllBytes(new File("/proc/self/stat").toPath()), StandardCharsets.UTF_8);
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        try (OutputStream marker = context.openFileOutput("guard-enabled", Context.MODE_PRIVATE)) {
            marker.write(token.getBytes(StandardCharsets.UTF_8));
        }
        String command = "/system/bin/sh " + ProcessIdentity.quote(script.getAbsolutePath())
                + " --watch-app " + Process.myPid() + " " + ProcessIdentity.startTicks(stat)
                + " " + Process.myUid() + " " + token + " > " + ProcessIdentity.quote(logFile(context).getAbsolutePath())
                + " 2>&1 &";
        request(command);
        EventLog.write(context, "vendor start accepted; pid=" + Process.myPid());
    }

    static void stop(Context context) throws Exception {
        // The daemon also watches this marker: stopping remains effective if Binder is unavailable.
        context.deleteFile("guard-enabled");
        File script = new File(context.getFilesDir(), "power-guard.sh");
        if (!script.isFile()) return;
        request("/system/bin/sh " + ProcessIdentity.quote(script.getAbsolutePath())
                + " --stop >> " + ProcessIdentity.quote(logFile(context).getAbsolutePath()) + " 2>&1");
        EventLog.write(context, "vendor stop accepted");
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
