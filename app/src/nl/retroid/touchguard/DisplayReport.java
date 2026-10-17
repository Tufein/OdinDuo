package nl.retroid.touchguard;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import java.util.Locale;

/** Public Android display observations only; never changes display or USB state. */
final class DisplayReport {
    private DisplayReport() {}

    static String read(Context context) {
        StringBuilder report = new StringBuilder("Android displays (read only)\nDefault display ID: ")
                .append(Display.DEFAULT_DISPLAY).append('\n');
        if (context == null) return report.append("Display service: unavailable (no context)").toString();

        DisplayManager manager;
        try {
            manager = context.getSystemService(DisplayManager.class);
        } catch (RuntimeException failure) {
            return report.append("Display service: ").append(unavailable(failure)).toString();
        }
        if (manager == null) return report.append("Display service: unavailable").toString();

        Display defaultDisplay = null;
        try {
            defaultDisplay = manager.getDisplay(Display.DEFAULT_DISPLAY);
            report.append("Default display: ").append(defaultDisplay == null ? "absent" : "present").append('\n');
        } catch (RuntimeException failure) {
            report.append("Default display: ").append(unavailable(failure)).append('\n');
        }

        Display[] available = null;
        try {
            available = manager.getDisplays();
            report.append("Available display IDs: ");
            if (available == null) {
                report.append("unavailable");
            } else {
                boolean any = false;
                for (Display display : available) {
                    if (display == null) continue;
                    if (any) report.append(", ");
                    report.append(display.getDisplayId());
                    any = true;
                }
                if (!any) report.append("none");
            }
            report.append('\n');
        } catch (RuntimeException failure) {
            report.append("Available display IDs: ").append(unavailable(failure)).append('\n');
        }

        if (defaultDisplay != null) appendDisplay(report, defaultDisplay);
        if (available != null) {
            for (Display display : available) {
                if (display != null && (defaultDisplay == null
                        || display.getDisplayId() != Display.DEFAULT_DISPLAY)) appendDisplay(report, display);
            }
        }
        return report.append("Reported state may differ from physical panel behavior.").toString();
    }

    private static void appendDisplay(StringBuilder report, Display display) {
        report.append("Display ").append(display.getDisplayId());
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) report.append(" (default)");
        report.append(": ");
        try {
            report.append("valid=").append(display.isValid());
            int state = display.getState();
            report.append("; reported_state=").append(stateName(state)).append(" (").append(state).append(')');
            Display.Mode mode = display.getMode();
            if (mode == null) {
                report.append("; mode=unavailable");
            } else {
                report.append("; mode=").append(mode.getModeId())
                        .append("; physical=").append(mode.getPhysicalWidth()).append('x').append(mode.getPhysicalHeight())
                        .append("; refresh=").append(String.format(Locale.ROOT, "%.2f Hz", mode.getRefreshRate()));
            }
        } catch (RuntimeException failure) {
            report.append("; details=").append(unavailable(failure));
        }
        report.append('\n');
    }

    private static String unavailable(RuntimeException failure) {
        // Exception messages can contain vendor-specific identifiers; only export the type.
        return failure instanceof SecurityException ? "permission denied"
                : "unavailable (" + failure.getClass().getSimpleName() + ")";
    }

    private static String stateName(int state) {
        switch (state) {
            case Display.STATE_OFF: return "OFF";
            case Display.STATE_ON: return "ON";
            case Display.STATE_DOZE: return "DOZE";
            case Display.STATE_DOZE_SUSPEND: return "DOZE_SUSPEND";
            case Display.STATE_VR: return "VR";
            case Display.STATE_ON_SUSPEND: return "ON_SUSPEND";
            default: return "UNKNOWN";
        }
    }
}
