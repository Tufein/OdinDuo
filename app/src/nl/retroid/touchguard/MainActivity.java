package nl.retroid.touchguard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class MainActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView device;
    private TextView title;
    private Chip stateChip;
    private MaterialButton start;
    private MaterialButton stop;
    private String localError;
    private MaterialSwitch automatic;
    private TextView automaticSummary;
    private boolean updatingSwitch;
    private Boolean pendingAutomatic;
    private long modeRequestedAt;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
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
        automatic = findViewById(R.id.automatic);
        automaticSummary = findViewById(R.id.automatic_summary);
        ((TextView) findViewById(R.id.version)).setText(getString(R.string.version_label,
                BuildConfig.VERSION_NAME, getString(R.string.compatibility)));
        start.setOnClickListener(view -> startGuard());
        stop.setOnClickListener(view -> {
            startService(new Intent(this, GuardService.class).setAction(GuardService.STOP));
            stop.setEnabled(false);
        });
        automatic.setOnCheckedChangeListener((button, enabled) -> {
            if (!updatingSwitch) setAutomatic(enabled);
        });
        findViewById(R.id.appearance).setOnClickListener(view -> chooseAppearance());
        findViewById(R.id.help).setOnClickListener(view -> showHelp());
        refreshStatus();

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        handleIntent(getIntent());
    }

    private void refreshStatus() {
        GuardStatusStore.Status snapshot = GuardStatusStore.read(this);
        String message = localError != null && !snapshot.running ? localError : snapshot.text;
        if (!android.text.TextUtils.equals(status.getText(), message)) status.setText(message);
        String state = localError != null && !snapshot.running ? "error" : snapshot.state;
        boolean busy = snapshot.running;
        start.setEnabled(!busy);
        start.setVisibility(busy ? View.GONE : View.VISIBLE);
        stop.setVisibility(busy ? View.VISIBLE : View.GONE);
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
        boolean present = "active".equals(snapshot.state);
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice input = InputDevice.getDevice(id);
            if (input != null && UsbIdentity.matches(input.getVendorId(), input.getProductId())) {
                present = true;
                break;
            }
        }
        device.setText(present ? R.string.device_connected : R.string.device_disconnected);
        boolean enabled = GuardPreferences.automatic(this);
        if (pendingAutomatic != null && (enabled == pendingAutomatic
                || android.os.SystemClock.elapsedRealtime() - modeRequestedAt > 5000)) pendingAutomatic = null;
        updatingSwitch = true;
        automatic.setChecked(pendingAutomatic != null ? pendingAutomatic : enabled);
        automatic.setEnabled(pendingAutomatic == null && !"stopping".equals(state));
        updatingSwitch = false;
        automaticSummary.setText(enabled ? (busy ? R.string.automatic_on : R.string.automatic_paused) : R.string.automatic_off);
        findViewById(R.id.notification_hint).setVisibility(
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        ? View.GONE : View.VISIBLE);
    }

    private void setStatus(String state, String value) {
        localError = "error".equals(state) ? value : null;
        status.setText(value);
        title.setText("error".equals(state) ? R.string.title_error : R.string.title_idle);
        stateChip.setText("error".equals(state) ? R.string.chip_error : R.string.chip_idle);
        start.setEnabled(true);
        stop.setEnabled(false);
        pendingAutomatic = null;
        refreshStatus();
    }

    private void startGuard() {
        if (GuardStatusStore.read(this).running) return;
        localError = null;
        EventLog.write(this, "power protection requested");
        if (!VendorBridge.available()) {
            setStatus("error", getString(R.string.status_vendor_unavailable));
            return;
        }
        try {
            startForegroundService(new Intent(this, GuardService.class));
            start.setEnabled(false);
        } catch (RuntimeException exception) {
            setStatus("error", getString(R.string.status_start_error, exception.getMessage()));
            EventLog.write(this, "service start failed " + exception);
        }
    }

    private void setAutomatic(boolean enabled) {
        localError = null;
        if (enabled && !VendorBridge.available()) {
            setStatus("error", getString(R.string.status_vendor_unavailable));
            return;
        }
        pendingAutomatic = enabled;
        modeRequestedAt = android.os.SystemClock.elapsedRealtime();
        automatic.setEnabled(false);
        try {
            startForegroundService(new Intent(this, GuardService.class).setAction(
                    enabled ? GuardService.AUTOMATIC_ON : GuardService.AUTOMATIC_OFF));
        } catch (RuntimeException exception) {
            setStatus("error", getString(R.string.status_start_error, exception.getMessage()));
        }
    }

    private void handleIntent(Intent intent) {
        if (intent.getBooleanExtra("start_guard", false)) startGuard();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction()) && GuardPreferences.automatic(this)) {
            UsbDevice usb = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
            if (usb != null && UsbIdentity.matches(usb.getVendorId(), usb.getProductId())) startGuard();
        }
    }

    private void chooseAppearance() {
        int current = getSharedPreferences("appearance", MODE_PRIVATE).getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int selected = current == AppCompatDelegate.MODE_NIGHT_NO ? 1 : current == AppCompatDelegate.MODE_NIGHT_YES ? 2 : 0;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.appearance_title)
                .setSingleChoiceItems(new String[]{getString(R.string.theme_system), getString(R.string.theme_light),
                        getString(R.string.theme_dark)}, selected, (dialog, which) -> {
                    int mode = which == 1 ? AppCompatDelegate.MODE_NIGHT_NO : which == 2
                            ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    getSharedPreferences("appearance", MODE_PRIVATE).edit().putInt("theme", mode).apply();
                    dialog.dismiss();
                    AppCompatDelegate.setDefaultNightMode(mode);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.help_title).setMessage(R.string.help_body)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.share_diagnostics, (dialog, which) -> shareDiagnostics()).show();
    }

    private void shareDiagnostics() {
        try {
            GuardStatusStore.Status snapshot = GuardStatusStore.read(this);
            String report = "OdinDuo " + BuildConfig.VERSION_NAME + "\nAndroid " + android.os.Build.VERSION.RELEASE
                    + "\nDevice: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                    + "\nAuto protect: " + GuardPreferences.automatic(this) + "\nState: " + snapshot.state
                    + "\n" + snapshot.text + "\n\nPower guard\n" + VendorBridge.readLog(this);
            File events = new File(getFilesDir(), "guard-events.txt");
            if (events.isFile()) {
                try (java.io.RandomAccessFile input = new java.io.RandomAccessFile(events, "r")) {
                    int length = (int) Math.min(input.length(), 32768);
                    input.seek(input.length() - length);
                    byte[] bytes = new byte[length];
                    input.readFully(bytes);
                    report += "\nApp events\n" + new String(bytes, StandardCharsets.UTF_8);
                }
            }
            report = report.replaceAll("(?i)\\b[a-f0-9]{32}\\b", "[session]");
            File directory = new File(getCacheDir(), "diagnostics");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create export directory");
            File output = new File(directory, "OdinDuo-diagnostics.txt");
            Files.write(output.toPath(), report.getBytes(StandardCharsets.UTF_8));
            android.net.Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".diagnostics", output);
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newRawUri("OdinDuo diagnostics", uri));
            startActivity(Intent.createChooser(share, getString(R.string.share_chooser)));
        } catch (Exception exception) {
            EventLog.write(this, "diagnostics export failed " + exception);
            Toast.makeText(this, R.string.share_error, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        GuardStatusStore.Status snapshot = GuardStatusStore.read(this);
        if (GuardPreferences.automatic(this) && !snapshot.running && !"error".equals(snapshot.state)) startGuard();
        handler.post(refresh);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
