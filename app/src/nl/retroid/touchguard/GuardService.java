package nl.retroid.touchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;

public final class GuardService extends Service {
    private static final String CHANNEL = "touch-guard";
    static final String STOP = "nl.retroid.touchguard.STOP";
    private HandlerThread thread;
    private Handler worker;
    private volatile boolean stopped;
    private boolean started;
    private long launchTime;
    private String latestStatus = "";
    private String finalStatus;
    private String finalState = "idle";

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            try {
                String log = VendorBridge.readLog(GuardService.this);
                if (log.contains("FAILED") || log.contains("readback failed")
                        || log.contains("already exists") || log.contains("verification failed")
                        || log.contains("rejected unexpected")) {
                    finish(getString(R.string.status_guard_error), "error");
                    return;
                }
                if (log.contains("GUARD stopped")) {
                    finish(getString(R.string.status_detached), "idle");
                    return;
                }
                if (log.contains("STATE control=on runtime=active host=active")) {
                    status("active", R.string.status_active);
                } else if (log.contains("RDS detected")) {
                    status("starting", R.string.status_detected);
                } else if (log.contains("READY waiting for RDS")) {
                    status("waiting", R.string.status_waiting);
                } else if (SystemClock.elapsedRealtime() - launchTime > 15000) {
                    finish(getString(R.string.status_launch_timeout), "error");
                    return;
                }
                worker.postDelayed(this, 1000);
            } catch (Exception exception) {
                EventLog.write(GuardService.this, "guard poll error " + exception);
                finish(getString(R.string.status_log_error), "error");
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        finalStatus = getString(R.string.status_stopped);
        thread = new HandlerThread("retroid-power-guard");
        thread.start();
        worker = new Handler(thread.getLooper());
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        EventLog.write(this, "power service created; Android " + Build.VERSION.RELEASE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (started) return START_STICKY;
        if (intent == null && !VendorBridge.guardAlive(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        started = true;
        try {
            Notification notification = notification(getString(R.string.status_preparing));
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, notification);
            }
        } catch (RuntimeException exception) {
            EventLog.write(this, "foreground service denied " + exception);
            finalStatus = getString(R.string.status_fgs_error);
            finalState = "error";
            stopSelf();
            return START_NOT_STICKY;
        }
        GuardStatusStore.write(this, true, "starting", getString(R.string.status_starting));
        worker.post(() -> {
            if (stopped) return;
            try {
                status("starting", R.string.status_starting);
                VendorBridge.start(this);
                launchTime = SystemClock.elapsedRealtime();
                worker.postDelayed(poll, 1000);
            } catch (Exception exception) {
                EventLog.write(this, "vendor launch failed " + exception);
                finish(getString(R.string.status_start_error, exception.getMessage()), "error");
            }
        });
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        EventLog.write(this, "app task closed; protection remains active");
        super.onTaskRemoved(rootIntent);
    }

    private void finish(String text, String state) {
        finalStatus = text;
        finalState = state;
        stopSelf();
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, GuardService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_touch)
                .setContentTitle(getString(R.string.app_name)).setContentText(text)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.notification_stop), stop).build()).build();
    }

    private void status(String state, int resource) {
        String text = getString(resource);
        if (text.equals(latestStatus)) return;
        latestStatus = text;
        GuardStatusStore.write(this, true, state, text);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notification(text));
        EventLog.write(this, "status " + text);
    }

    @Override public void onDestroy() {
        stopped = true;
        worker.removeCallbacksAndMessages(null);
        GuardStatusStore.write(this, true, "stopping", getString(R.string.status_stopping));
        worker.post(() -> {
            try {
                VendorBridge.stop(this);
                String log = VendorBridge.readLog(this);
                if (log.contains("RESTORE FAILED")) {
                    finalStatus = getString(R.string.status_restore_error);
                    finalState = "error";
                }
            } catch (Exception exception) {
                EventLog.write(this, "vendor stop failed " + exception);
                finalStatus = getString(R.string.status_stop_error);
                finalState = "error";
            }
            GuardStatusStore.write(this, false, finalState, finalStatus);
            EventLog.write(this, "service stopped: " + finalStatus);
            thread.quitSafely();
        });
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
