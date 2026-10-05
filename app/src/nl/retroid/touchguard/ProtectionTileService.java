package nl.retroid.touchguard;

import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.os.SystemClock;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** SystemUI controls protection without requiring the dashboard to stay open. */
public final class ProtectionTileService extends TileService {
    private boolean listening;
    private Boolean pendingRunning;
    private long requestedAt;

    static void refresh(Context context) {
        try {
            TileService.requestListeningState(context, new ComponentName(context, ProtectionTileService.class));
        } catch (RuntimeException exception) {
            android.util.Log.w("RetroidTouchGuard", "Tile refresh deferred", exception);
        }
    }

    @Override public void onStartListening() {
        listening = true;
        updateTile();
    }

    @Override public void onStopListening() {
        listening = false;
    }

    @Override public void onTileAdded() { refresh(this); }

    @Override public void onClick() {
        if (isLocked()) unlockAndRun(this::toggleProtection);
        else toggleProtection();
    }

    private void toggleProtection() {
        // SystemUI can stop listening while this tile is on another QS page.
        // Refresh acknowledgement here too, so a later click cannot stay blocked.
        updateTile();
        GuardStatusStore.Status snapshot = GuardStatusStore.read(this);
        if (pendingRunning != null || "stopping".equals(snapshot.state)) return;
        if (!snapshot.running && !VendorBridge.available()) {
            Toast.makeText(this, R.string.status_vendor_unavailable, Toast.LENGTH_LONG).show();
            updateTile();
            return;
        }
        try {
            boolean enable = !snapshot.running;
            Intent command = new Intent(this, GuardService.class).setAction(
                    enable ? GuardService.AUTOMATIC_ON : GuardService.STOP);
            if (enable) startForegroundService(command);
            else startService(command);
            pendingRunning = enable;
            requestedAt = SystemClock.uptimeMillis();
            EventLog.write(this, "Quick Settings requested protection " + (enable ? "on" : "off"));
            updateTile();
        } catch (RuntimeException exception) {
            EventLog.write(this, "Quick Settings command failed " + exception);
            Toast.makeText(this, R.string.tile_action_failed, Toast.LENGTH_LONG).show();
            updateTile();
        }
    }

    private void updateTile() {
        GuardStatusStore.Status snapshot = GuardStatusStore.read(this);
        if (pendingRunning != null && (snapshot.running == pendingRunning
                || SystemClock.uptimeMillis() - requestedAt >= 8000)) pendingRunning = null;
        if (!listening) return;
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean unavailable = !snapshot.running && !VendorBridge.available();
        int subtitle = R.string.tile_off;
        if (unavailable) subtitle = R.string.tile_unavailable;
        else if (pendingRunning != null) subtitle = pendingRunning ? R.string.tile_starting : R.string.tile_stopping;
        else switch (snapshot.state) {
            case "active": subtitle = R.string.chip_active; break;
            case "waiting": subtitle = R.string.tile_waiting; break;
            case "starting": subtitle = R.string.tile_starting; break;
            case "recovering": subtitle = R.string.chip_recovering; break;
            case "stopping": subtitle = R.string.tile_stopping; break;
            case "error": subtitle = R.string.chip_error; break;
            default: break;
        }
        tile.setLabel(getString(R.string.app_name));
        tile.setSubtitle(getString(subtitle));
        tile.setContentDescription(getString(R.string.app_name) + ": " + getString(subtitle));
        // Keep pending commands clickable: a non-visible tile may receive no listening
        // callbacks, and SystemUI otherwise caches Unavailable before it can acknowledge.
        tile.setState(unavailable || "stopping".equals(snapshot.state) ? Tile.STATE_UNAVAILABLE
                : snapshot.running || Boolean.TRUE.equals(pendingRunning) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override public void onDestroy() {
        listening = false;
        super.onDestroy();
    }
}
