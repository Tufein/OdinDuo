import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Runs as the already-authorized adb shell UID, never as root. */
public final class UsbRecoveryConsole {
    private static Context context;
    private static FileInputStream held;

    private static void log(String value) {
        System.out.println(System.currentTimeMillis() + " " + value);
        System.out.flush();
    }

    private static String read(File directory, String name) {
        try {
            return new String(Files.readAllBytes(new File(directory, name).toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) { return "<unreadable>"; }
    }

    private static File findRds() {
        File[] devices = new File("/sys/bus/usb/devices").listFiles();
        if (devices == null) return null;
        for (File device : devices) {
            if ("222a".equals(read(device, "idVendor")) && "0001".equals(read(device, "idProduct"))) return device;
        }
        return null;
    }

    private static String deviceNode(File rds) {
        return String.format(java.util.Locale.ROOT, "/dev/bus/usb/%03d/%03d",
                Integer.parseInt(read(rds, "busnum")), Integer.parseInt(read(rds, "devnum")));
    }

    private static void describe(File rds) {
        log("RDS sysfs=" + rds + " node=" + deviceNode(rds)
                + " deviceClass=" + read(rds, "bDeviceClass")
                + " deviceSubclass=" + read(rds, "bDeviceSubClass")
                + " runtime=" + read(rds, "power/runtime_status"));
        File[] interfaces = new File("/sys/bus/usb/devices").listFiles();
        if (interfaces != null) for (File intf : interfaces) {
            if (intf.getName().startsWith(rds.getName() + ":")) {
                log("interface=" + intf.getName() + " class=" + read(intf, "bInterfaceClass")
                        + " subclass=" + read(intf, "bInterfaceSubClass"));
            }
        }
    }

    private static void close() {
        if (held != null) {
            try { held.close(); } catch (Exception ignored) { }
            held = null;
            log("USB descriptor closed");
        }
    }

    private static int enableData(Object port, boolean enabled) throws Exception {
        return (Integer) port.getClass().getMethod("enableUsbData", boolean.class).invoke(port, enabled);
    }

    private static boolean restoreData(Object port) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                int result = enableData(port, true);
                log("USB data restore result=" + result);
                if (result == 0) return true;
                Thread.sleep(1000);
            } catch (Exception exception) { log("USB restore error=" + exception); }
        }
        log("USB RESTORE NOT CONFIRMED");
        return false;
    }

    private static boolean recoverData(List<Object> ports) throws Exception {
        File target = findRds();
        if (target == null) { log("skip recovery: no RDS device; no USB change made"); return false; }
        if (ports.size() != 1) { log("skip recovery: expected exactly one USB port"); return false; }
        Object port = ports.get(0);
        Object status = port.getClass().getMethod("getStatus").invoke(port);
        if (status == null) { log("skip recovery: no port status"); return false; }
        int role = (Integer) status.getClass().getMethod("getCurrentDataRole").invoke(status);
        int bits = (Integer) status.getClass().getMethod("getUsbDataStatus").invoke(status);
        int enabled = status.getClass().getField("DATA_STATUS_ENABLED").getInt(null);
        // RDS must still be connected and the Odin must be the host, not an adb device on the Mac.
        if (role != 1 || (bits & enabled) == 0 || findRds() == null) {
            log("skip recovery: port not an enabled RDS host");
            return false;
        }
        new ProcessBuilder("/system/bin/sh", "-c",
                "sleep 12; CLASSPATH=/data/local/tmp/retroid-usb-console.dex app_process / UsbRecoveryConsole --restore-data >> /data/local/tmp/retroid-touch-logs/usb-data-restore.txt 2>&1").start();
        log("recovery watchdog scheduled; requesting USB data off");
        try {
            int result = enableData(port, false);
            log("USB data off result=" + result);
            if (result != 0) return false;
            Thread.sleep(1000);
        } finally {
            restoreData(port);
        }
        Thread.sleep(3000);
        File resumed = findRds();
        log("post-recovery RDS=" + resumed
                + " runtime=" + (resumed == null ? "absent" : read(resumed, "power/runtime_status")));
        return true;
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        UsbManager usb = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        int resource = context.getResources().getIdentifier("config_usbHostDenylist", "array", "android");
        if (resource != 0) log("host denylist=" + Arrays.toString(context.getResources().getStringArray(resource)));
        log("UsbManager devices=" + usb.getDeviceList().keySet());
        for (Method method : UsbManager.class.getMethods()) {
            if (method.getName().equals("getPorts") || method.getName().equals("enableUsbDataSignal")) log("API " + method);
        }
        @SuppressWarnings("unchecked")
        List<Object> ports = (List<Object>) UsbManager.class.getMethod("getPorts").invoke(usb);
        for (Object port : ports) log("port=" + port);
        if (args.length > 0 && "--restore-data".equals(args[0])) {
            if (ports.size() != 1) throw new IllegalStateException("Restore requires the original single port");
            restoreData(ports.get(0));
            return;
        }
        if (args.length > 0 && "--recover-data-once".equals(args[0])) {
            recoverData(ports);
            return;
        }
        if (args.length > 0 && "--check-data-api".equals(args[0])) {
            if (ports.size() != 1) throw new IllegalStateException("Expected exactly one USB port");
            Object port = ports.get(0);
            Object status = port.getClass().getMethod("getStatus").invoke(port);
            log("port status=" + status);
            if (status == null) throw new IllegalStateException("No USB port status");
            int dataStatus = (Integer) status.getClass().getMethod("getUsbDataStatus").invoke(status);
            int enabled = status.getClass().getField("DATA_STATUS_ENABLED").getInt(null);
            if ((dataStatus & enabled) == 0) throw new IllegalStateException("USB data was not already enabled; skipping check");
            // Request the existing state only. This probe never disables the Mac's USB connection.
            int result = (Integer) port.getClass().getMethod("enableUsbData", boolean.class).invoke(port, true);
            log("enableUsbData(true) result=" + result + " (0=success, 2=not supported)");
            return;
        }
        boolean watchData = args.length > 0 && "--watch-data".equals(args[0]);
        if (args.length == 0 || (!"--watch-open".equals(args[0]) && !watchData)) return;
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        long end = SystemClock.elapsedRealtime() + 30 * 60 * 1000;
        boolean previousAwake = power.isInteractive();
        boolean tried = false;
        String previousNode = "";
        boolean sleptWithRds = false;
        long wakeDeadline = 0;
        Runtime.getRuntime().addShutdownHook(new Thread(UsbRecoveryConsole::close));
        try {
            while (SystemClock.elapsedRealtime() < end) {
                boolean awake = power.isInteractive();
                File rds = findRds();
                if (awake != previousAwake) {
                    log("screen awake=" + awake);
                    close();
                    tried = false;
                    previousAwake = awake;
                    if (!awake && rds != null) sleptWithRds = true;
                    if (awake && sleptWithRds) wakeDeadline = SystemClock.elapsedRealtime() + 7000;
                }
                String node = rds == null ? "" : deviceNode(rds);
                if (!node.equals(previousNode)) {
                    close();
                    tried = false;
                    previousNode = node;
                    log("RDS node=" + node);
                    if (rds != null) describe(rds);
                }
                if (watchData && awake && rds != null && wakeDeadline > 0
                        && SystemClock.elapsedRealtime() >= wakeDeadline) {
                    if ("suspended".equals(read(rds, "power/runtime_status"))) {
                        log("RDS suspended after wake; one-shot USB data recovery");
                        recoverData(ports);
                        break;
                    }
                }
                if (!watchData && awake && rds != null && !tried) {
                    tried = true;
                    log("UsbManager devices with RDS=" + usb.getDeviceList().keySet());
                    try {
                        // Opening usbfs asks the kernel to resume; no interface is claimed and no bytes are written.
                        held = new FileInputStream(node);
                        log("read-only USB open succeeded; runtime=" + read(rds, "power/runtime_status"));
                    } catch (Exception exception) {
                        log("read-only USB open unavailable: " + exception);
                    }
                }
                Thread.sleep(1000);
            }
        } finally { close(); }
        log("watch finished");
    }
}
