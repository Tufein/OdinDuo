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
import java.util.concurrent.CountDownLatch;

public final class GuardService extends Service {
    private static final String CHANNEL = "touch-guard";
    private static final String ALERT_CHANNEL = "touch-alerts";
    static final String STOP = "nl.retroid.touchguard.STOP";
    static final String AUTOMATIC_ON = "nl.retroid.touchguard.AUTOMATIC_ON";
    static final String AUTOMATIC_OFF = "nl.retroid.touchguard.AUTOMATIC_OFF";
    private static volatile CountDownLatch pendingRestoration = new CountDownLatch(0);
    private CountDownLatch startupBarrier;
    private HandlerThread thread;
    private Handler worker;
    private volatile boolean stopped;
    private volatile boolean ending;
    private boolean started;
    private boolean automatic;
    private final GuardHealth health = new GuardHealth();
    private String latestStatus = "";
    private int finalResource = R.string.status_stopped;
    private Object[] finalArguments = new Object[0];
    private String finalState = "idle";

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (stopped || ending) return;
            try {
                String log = VendorBridge.readLog(GuardService.this);
                GuardHeartbeat sample = VendorBridge.heartbeat(GuardService.this);
                GuardLogState.State observation = sample.observation(log);
                // A historical successful power sample cannot prove the root helper is alive.
                if (observation != GuardLogState.State.ERROR && observation != GuardLogState.State.STOPPED) {
                    GuardHealth.State heartbeat = health.observe(sample.alive,
                            SystemClock.uptimeMillis());
                    if (heartbeat == GuardHealth.State.LOST) {
                        if (!automatic || !health.allowRecovery(SystemClock.uptimeMillis())) {
                            finish(R.string.status_helper_lost, "error");
                            return;
                        }
                        status("recovering", R.string.status_recovering);
                        EventLog.write(GuardService.this, "helper heartbeat lost; confirming restoration before recovery");
                        VendorBridge.stop(GuardService.this);
                        if (stopped || ending) return;
                        launchGuard();
                        return;
                    }
                    if (heartbeat != GuardHealth.State.HEALTHY) {
                        status(heartbeat == GuardHealth.State.STARTING ? "starting" : "recovering",
                                heartbeat == GuardHealth.State.STARTING ? R.string.status_starting : R.string.status_recovering);
                        worker.postDelayed(this, 1000);
                        return;
                    }
                }
                switch (observation) {
                    case ERROR:
                        finish(R.string.status_guard_error, "error");
                        return;
                    case STOPPED:
                        if (!automatic) {
                            finish(R.string.status_detached, "idle");
                            return;
                        }
                        if (!log.contains("RDS detached/replaced; restoring")
                                && !health.allowRecovery(SystemClock.uptimeMillis())) {
                            finish(R.string.status_helper_lost, "error");
                            return;
                        }
                        // Wait for complete restoration before starting the next connection lease.
                        // The existing root guard scans for RDS without changing power while waiting.
                        status("waiting", R.string.status_auto_waiting);
                        VendorBridge.stop(GuardService.this);
                        if (stopped || ending) return;
                        EventLog.write(GuardService.this, "automatic mode rearmed after display detach");
                        launchGuard();
                        return;
                    case ACTIVE: status("active", R.string.status_active); break;
                    case STARTING: status("starting", R.string.status_detected); break;
                    case WAITING:
                        status("waiting", automatic ? R.string.status_auto_waiting : R.string.status_waiting);
                        break;
                    case EMPTY:
                        if (health.startupExpired(SystemClock.uptimeMillis())) {
                            finish(R.string.status_launch_timeout, "error");
                            return;
                        }
                        status("starting", R.string.status_starting);
                        break;
                }
                worker.postDelayed(this, 1000);
            } catch (Exception exception) {
                EventLog.write(GuardService.this, "guard poll error " + exception);
                finish(R.string.status_log_error, "error");
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        startupBarrier = pendingRestoration;
        automatic = GuardPreferences.automatic(this);
        thread = new HandlerThread("retroid-power-guard");
        thread.start();
        worker = new Handler(thread.getLooper());
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(
                new NotificationChannel(ALERT_CHANNEL, getString(R.string.notification_alert_channel), NotificationManager.IMPORTANCE_DEFAULT));
        EventLog.write(this, "power service created; Android " + Build.VERSION.RELEASE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            ending = true;
            worker.post(() -> {
                try { GuardPreferences.setAutomatic(this, false); automatic = false; }
                catch (Exception exception) {
                    EventLog.write(this, "cannot save automatic setting " + exception);
                    finalResource = R.string.status_settings_error;
                    finalState = "error";
                }
                stopSelf();
            });
            return START_NOT_STICKY;
        }
        if (ending || stopped) return START_NOT_STICKY;
        String action = intent == null ? null : intent.getAction();
        Boolean requestedMode = AUTOMATIC_ON.equals(action) ? Boolean.TRUE
                : AUTOMATIC_OFF.equals(action) ? Boolean.FALSE : null;
        if (started) {
            if (requestedMode != null) worker.post(() -> updateAutomatic(requestedMode));
            return START_STICKY;
        }
        if (intent == null && !automatic && !VendorBridge.guardAlive(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        started = true;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2);
        try {
            Notification notification = notification(getString(R.string.status_preparing));
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(1, notification);
            }
        } catch (RuntimeException exception) {
            EventLog.write(this, "foreground service denied " + exception);
            finalResource = R.string.status_fgs_error;
            finalState = "error";
            stopSelf();
            return START_NOT_STICKY;
        }
        GuardStatusStore.write(this, true, "starting", R.string.status_starting);
        worker.post(() -> {
            if (stopped || ending) return;
            try {
                // Android can create the next service instance before the previous asynchronous
                // onDestroy cleanup finishes. Never let its Stop tear down a new USB lease.
                startupBarrier.await();
                if (stopped || ending) return;
                // Cleanup can post its failure after this instance's initial cancellation.
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(2);
                if (requestedMode != null && !updateAutomatic(requestedMode)) return;
                status("starting", R.string.status_starting);
                launchGuard();
            } catch (Exception exception) {
                EventLog.write(this, "vendor launch failed " + exception);
                finish(R.string.status_start_error, "error", exception.getMessage());
            }
        });
        return START_STICKY;
    }

    private boolean updateAutomatic(boolean enabled) {
        try {
            GuardPreferences.setAutomatic(this, enabled);
            automatic = enabled;
            EventLog.write(this, "automatic protection " + (enabled ? "enabled" : "disabled"));
            GuardLogState.State observed = VendorBridge.heartbeat(this).observation(VendorBridge.readLog(this));
            if (!enabled && observed != GuardLogState.State.ACTIVE) {
                finish(R.string.status_stopped, "idle");
                return false;
            }
            if (enabled && observed == GuardLogState.State.WAITING) {
                status("waiting", R.string.status_auto_waiting);
            }
            return true;
        } catch (Exception exception) {
            EventLog.write(this, "automatic setting failed " + exception);
            finish(R.string.status_settings_error, "error");
            return false;
        }
    }

    private void launchGuard() throws Exception {
        if (stopped || ending) return;
        VendorBridge.start(this);
        health.launched(SystemClock.uptimeMillis());
        worker.postDelayed(poll, 1000);
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        EventLog.write(this, "app task closed; protection remains active");
        super.onTaskRemoved(rootIntent);
    }

    private void finish(int resource, String state, Object... args) {
        ending = true;
        finalResource = resource;
        finalArguments = args;
        finalState = state;
        stopSelf();
    }

    private Notification notification(String text) {
        android.content.Context language = AppLanguage.current(this);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, GuardService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_touch)
                .setContentTitle(language.getString(R.string.app_name)).setContentText(text)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, language.getString(R.string.notification_stop), stop).build()).build();
    }

    private void status(String state, int resource) {
        String text = AppLanguage.current(this).getString(resource);
        if (text.equals(latestStatus)) return;
        latestStatus = text;
        GuardStatusStore.write(this, true, state, resource);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(1, notification(text));
        EventLog.write(this, "status " + text);
    }

    private void showFailureNotification() {
        if (!"error".equals(finalState)) return;
        try {
            android.content.Context language = AppLanguage.current(this);
            String finalStatus = language.getString(finalResource, finalArguments);
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            PendingIntent retry = PendingIntent.getForegroundService(this, 2,
                    new Intent(this, GuardService.class), PendingIntent.FLAG_IMMUTABLE);
            Notification failure = new Notification.Builder(this, ALERT_CHANNEL).setSmallIcon(R.drawable.ic_touch)
                    .setContentTitle(language.getString(R.string.failure_title)).setContentText(finalStatus)
                    .setStyle(new Notification.BigTextStyle().bigText(finalStatus))
                    .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
                    .addAction(new Notification.Action.Builder(null, language.getString(R.string.retry_protection), retry).build()).build();
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(2, failure);
        } catch (RuntimeException exception) {
            EventLog.write(this, "cannot show protection failure " + exception);
        }
    }

    @Override public void onDestroy() {
        stopped = true;
        CountDownLatch restoration = new CountDownLatch(1);
        pendingRestoration = restoration;
        worker.removeCallbacksAndMessages(null);
        GuardStatusStore.write(this, true, "stopping", R.string.status_stopping);
        worker.post(() -> {
            try {
                VendorBridge.stop(this);
                // stop() checks a unique acknowledgement and pending snapshots. An older
                // RESTORE FAILED line must not override a later successful recovery.
            } catch (Exception exception) {
                EventLog.write(this, "vendor stop failed " + exception);
                finalResource = R.string.status_stop_error;
                finalArguments = new Object[0];
                finalState = "error";
            }
            GuardStatusStore.write(this, false, finalState, finalResource, finalArguments);
            showFailureNotification();
            EventLog.write(this, "service stopped: " + AppLanguage.english(this).getString(finalResource, finalArguments));
            restoration.countDown();
            thread.quitSafely();
        });
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
