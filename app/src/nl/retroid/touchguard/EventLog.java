package nl.retroid.touchguard;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class EventLog {
    static synchronized void write(Context context, String event) {
        String line = System.currentTimeMillis() + " " + event + "\n";
        Log.i("RetroidTouchGuard", event);
        try {
            File file = new File(context.getFilesDir(), "guard-events.txt");
            if (file.length() > 512 * 1024) {
                File old = new File(context.getFilesDir(), "guard-events.previous.txt");
                if (old.exists()) old.delete();
                if (!file.renameTo(old)) return;
            }
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            Log.e("RetroidTouchGuard", "Cannot save event", exception);
        }
    }
}
