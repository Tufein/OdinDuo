package nl.retroid.touchguard;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

public final class MainActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView device;
    private TextView title;
    private Chip stateChip;
    private MaterialButton start;
    private MaterialButton stop;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        SharedPreferences preferences = getSharedPreferences("guard", MODE_PRIVATE);
        if (!GuardService.isBusy()) {
            if (preferences.getBoolean("running", false)) {
                preferences.edit().putBoolean("running", false).putString("state", "idle")
                        .putString("status", getString(R.string.status_restarted)).apply();
            } else if (preferences.getInt("ui_version", 0) < 4) {
                // Replace persisted Dutch copy when upgrading the original working app.
                preferences.edit().putString("state", "idle")
                        .putString("status", getString(R.string.status_idle)).apply();
            }
        }
        preferences.edit().putInt("ui_version", 4).apply();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
        status = findViewById(R.id.status);
        device = findViewById(R.id.device);
        title = findViewById(R.id.state_title);
        stateChip = findViewById(R.id.state_chip);
        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);
        ((TextView) findViewById(R.id.version)).setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
        start.setOnClickListener(view -> startGuard());
        stop.setOnClickListener(view -> {
            if (!stopService(new Intent(this, GuardService.class))) {
                setStatus("idle", getString(R.string.status_not_running));
            }
        });
        refreshStatus();

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        // Explicit local adb action; opening the app normally does not start protection.
        if (getIntent().getBooleanExtra("start_guard", false)) startGuard();
    }

    private void refreshStatus() {
        SharedPreferences preferences = getSharedPreferences("guard", MODE_PRIVATE);
        status.setText(preferences.getString("status", getString(R.string.status_idle)));
        String state = preferences.getString("state", "idle");
        boolean busy = GuardService.isBusy();
        start.setEnabled(!busy);
        stop.setEnabled(busy && !"stopping".equals(state));
        int titleResource = R.string.title_idle;
        int chipResource = R.string.chip_idle;
        switch (state) {
            case "waiting": titleResource = R.string.title_waiting; chipResource = R.string.chip_waiting; break;
            case "starting": titleResource = R.string.title_starting; chipResource = R.string.chip_waiting; break;
            case "active": titleResource = R.string.title_active; chipResource = R.string.chip_active; break;
            case "stopping": titleResource = R.string.title_stopping; chipResource = R.string.chip_waiting; break;
            case "error": titleResource = R.string.title_error; chipResource = R.string.chip_error; break;
            default: break;
        }
        title.setText(titleResource);
        stateChip.setText(chipResource);
        boolean present = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice input = InputDevice.getDevice(id);
            if (input != null && UsbIdentity.matches(input.getVendorId(), input.getProductId())) {
                present = true;
                break;
            }
        }
        device.setText(present ? R.string.device_connected : R.string.device_disconnected);
    }

    private void setStatus(String state, String value) {
        getSharedPreferences("guard", MODE_PRIVATE).edit().putString("state", state).putString("status", value).apply();
        refreshStatus();
    }

    private void startGuard() {
        if (GuardService.isBusy()) return;
        EventLog.write(this, "power protection requested");
        if (!VendorBridge.available()) {
            setStatus("error", getString(R.string.status_vendor_unavailable));
            return;
        }
        try {
            startForegroundService(new Intent(this, GuardService.class));
        } catch (RuntimeException exception) {
            setStatus("error", getString(R.string.status_start_error, exception.getMessage()));
            EventLog.write(this, "service start failed " + exception);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("start_guard", false)) startGuard();
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
