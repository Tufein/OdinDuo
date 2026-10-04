import android.os.IBinder;
import android.os.Parcel;

/** Fixed diagnostic/test entry points for the stock AYN PServer service. */
public final class PServerConsole {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Use --probe, --guard, or --stop");
        final String command;
        switch (args[0]) {
            case "--probe":
                command = "/system/bin/sh /data/local/tmp/retroid-pserver-probe.sh"
                        + " > /data/local/tmp/retroid-touch-logs/pserver-probe.txt 2>&1";
                break;
            case "--guard":
                command = "/system/bin/sh /data/local/tmp/retroid-power-guard.sh --watch"
                        + " > /data/local/tmp/retroid-touch-logs/power-guard.txt 2>&1 &";
                break;
            case "--stop":
                command = "/system/bin/sh /data/local/tmp/retroid-power-guard.sh --stop"
                        + " >> /data/local/tmp/retroid-touch-logs/power-guard-stop.txt 2>&1";
                break;
            default: throw new IllegalArgumentException("Unknown fixed operation");
        }
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "PServerBinder");
        if (service == null || !service.isBinderAlive()) {
            throw new IllegalStateException("The stock PServer service is unavailable");
        }
        // Stock vendor wire format documented by ClusterTune/O2P Tweaks; see README.
        Parcel request = Parcel.obtain();
        Parcel response = Parcel.obtain();
        try {
            request.writeStringArray(new String[]{command, "0"});
            boolean accepted = service.transact(0, request, response, 0);
            System.out.println("PServer diagnostic accepted=" + accepted
                    + "; check the diagnostic output for actual execution");
            if (!accepted) throw new IllegalStateException("Diagnostic request was rejected");
        } finally {
            response.recycle();
            request.recycle();
        }
    }
}
