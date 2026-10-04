package nl.retroid.touchguard;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Only the service worker writes this file. Every process reads a fresh atomic snapshot. */
final class GuardPreferences {
    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "automatic-protection"));
    }

    static boolean automatic(Context context) {
        try { return "enabled".equals(new String(file(context).readFully(), StandardCharsets.UTF_8)); }
        catch (Exception ignored) { return false; }
    }

    static void setAutomatic(Context context, boolean enabled) throws Exception {
        AtomicFile file = file(context);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write((enabled ? "enabled" : "disabled").getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (Exception exception) {
            if (output != null) file.failWrite(output);
            throw exception;
        }
    }
}
