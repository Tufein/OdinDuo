package nl.retroid.touchguard;

import static org.junit.Assert.*;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import java.time.Duration;
import org.junit.Test;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowSystemClock;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainActivityTest {
    @Before public void prepareBoot() {
        android.provider.Settings.Global.putInt(RuntimeEnvironment.getApplication().getContentResolver(),
                android.provider.Settings.Global.BOOT_COUNT, 7);
        RuntimeEnvironment.getApplication().getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("notifications_asked", true).commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(OnboardingDialog.COMPLETE, true).commit();
    }

    @Test public void setupIsReachableFromDashboard() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertTrue(activity.findViewById(R.id.setup).performClick());
            AlertDialog dialog = ((AlertDialog) ShadowDialog.getLatestDialog());
            assertTrue(dialog.isShowing());
            assertEquals(activity.getString(R.string.tile_add), dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        }
    }

    @Test public void failureOffersRetrySetupAndDiagnostics() {
        Bundle saved = new Bundle();
        saved.putString("local_error", "AYN service unavailable");
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible()) {
            MainActivity activity = controller.get();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.error_actions).getVisibility());
            assertEquals(activity.getString(R.string.retry_protection), ((TextView) activity.findViewById(R.id.start)).getText().toString());
            assertEquals(View.GONE, activity.findViewById(R.id.progress).getVisibility());
            activity.findViewById(R.id.error_setup).performClick();
            assertTrue(((AlertDialog) ShadowDialog.getLatestDialog()).isShowing());
        }
    }

    @Test public void pendingStartSurvivesRecreationAndTimesOut() {
        Bundle saved = new Bundle();
        saved.putBoolean("pending_start", true);
        saved.putString("request_boot", "boot:7");
        saved.putLong("action_requested_at", android.os.SystemClock.elapsedRealtime());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible()) {
            MainActivity activity = controller.get();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.progress).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.start).getVisibility());
            assertFalse(activity.findViewById(R.id.start).isEnabled());
            assertEquals(activity.getString(R.string.starting_protection), ((TextView) activity.findViewById(R.id.start)).getText().toString());
            assertFalse(activity.findViewById(R.id.stop).isEnabled());
            assertFalse(activity.findViewById(R.id.automatic).isEnabled());
            controller.recreate();
            activity = controller.get();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.progress).getVisibility());
            ShadowSystemClock.advanceBy(Duration.ofSeconds(11));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(View.GONE, activity.findViewById(R.id.progress).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.start).getVisibility());
            assertTrue(activity.findViewById(R.id.start).isEnabled());
        }
    }

    @Test public void pendingAutomaticShowsFeedbackAndSurvivesRecreation() {
        Bundle saved = new Bundle();
        saved.putBoolean("pending_automatic", true);
        saved.putString("request_boot", "boot:7");
        saved.putLong("mode_requested_at", android.os.SystemClock.elapsedRealtime());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible()) {
            MainActivity activity = controller.get();
            assertFalse(activity.findViewById(R.id.automatic).isEnabled());
            assertFalse(activity.findViewById(R.id.start).isEnabled());
            assertEquals(activity.getString(R.string.automatic_updating), ((TextView) activity.findViewById(R.id.automatic_summary)).getText().toString());
            controller.recreate();
            activity = controller.get();
            assertFalse(activity.findViewById(R.id.automatic).isEnabled());
            ShadowSystemClock.advanceBy(Duration.ofSeconds(11));
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(activity.findViewById(R.id.automatic).isEnabled());
            assertTrue(activity.findViewById(R.id.start).isEnabled());
            assertEquals(activity.getString(R.string.automatic_off), ((TextView) activity.findViewById(R.id.automatic_summary)).getText().toString());
            assertEquals(activity.getString(R.string.status_settings_error),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test public void pendingRequestsFromPreviousBootAreDiscarded() {
        Bundle saved = new Bundle();
        saved.putString("request_boot", "boot:6");
        saved.putBoolean("pending_start", true);
        saved.putBoolean("pending_automatic", true);
        saved.putLong("action_requested_at", android.os.SystemClock.elapsedRealtime());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible()) {
            MainActivity activity = controller.get();
            assertEquals(View.GONE, activity.findViewById(R.id.progress).getVisibility());
            assertTrue(activity.findViewById(R.id.start).isEnabled());
            assertTrue(activity.findViewById(R.id.automatic).isEnabled());
        }
    }

    @Test @Config(qualifiers = "nl") public void startingFeedbackUsesDutch() {
        Bundle saved = new Bundle();
        saved.putString("request_boot", "boot:7");
        saved.putBoolean("pending_start", true);
        saved.putLong("action_requested_at", android.os.SystemClock.elapsedRealtime());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible()) {
            assertEquals("Starten…", ((TextView) controller.get().findViewById(R.id.start)).getText().toString());
            assertEquals("Starten", ((TextView) controller.get().findViewById(R.id.state_chip)).getText().toString());
        }
    }

    @Test public void firstSetupStillWaitsForNotificationPermissionAfterRecreation() {
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(OnboardingDialog.COMPLETE, false).commit();
        Bundle saved = new Bundle();
        saved.putString("request_boot", "boot:7");
        saved.putBoolean("notification_request_pending", true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible()) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertNull(ShadowDialog.getLatestDialog());
            controller.recreate();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertNull(ShadowDialog.getLatestDialog());
            controller.get().onRequestPermissionsResult(1,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            assertTrue(ShadowDialog.getLatestDialog().isShowing());
        }
    }

    @Test @Config(qualifiers = "land") public void landscapeHasSetupAndErrorActions() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertNotNull(activity.findViewById(R.id.setup));
            assertNotNull(activity.findViewById(R.id.error_diagnostics));
            assertEquals(View.GONE, activity.findViewById(R.id.error_actions).getVisibility());
        }
    }

    @Test public void notificationHintOpensAppSettings() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            View hint = activity.findViewById(R.id.notification_hint);
            assertEquals(View.VISIBLE, hint.getVisibility());
            assertTrue(hint.isFocusable());
            hint.performClick();
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());
            assertEquals("package:nl.retroid.touchguard", intent.getDataString());
        }
    }

    @Test public void startIntentIsConsumedAndNotReplayedAfterRecreation() throws Exception {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra("start_guard", true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent).setup()) {
            MainActivity activity = controller.get();
            assertFalse(activity.getIntent().hasExtra("start_guard"));
            java.io.File events = new java.io.File(activity.getFilesDir(), "guard-events.txt");
            String before = new String(java.nio.file.Files.readAllBytes(events.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(before.contains("power protection requested"));
            controller.recreate();
            assertEquals(before, new String(java.nio.file.Files.readAllBytes(events.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test public void manualSetupDoesNotGetCoveredByDelayedOnboarding() {
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(OnboardingDialog.COMPLETE, false).commit();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.setup).performClick();
            android.app.Dialog setup = ShadowDialog.getLatestDialog();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertTrue(setup.isShowing());
            assertNull(activity.getSupportFragmentManager().findFragmentByTag(OnboardingDialog.TAG));
            assertSame(setup, ShadowDialog.getLatestDialog());
        }
    }

    @Test public void screenHelpDoesNotGetCoveredByDelayedOnboarding() {
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(OnboardingDialog.COMPLETE, false).commit();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            activity.findViewById(R.id.screen_help).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertNull(activity.getSupportFragmentManager().findFragmentByTag(OnboardingDialog.TAG));
            controller.pause().stop().start().resume().visible();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertNull(activity.getSupportFragmentManager().findFragmentByTag(OnboardingDialog.TAG));
            assertNotNull(activity.getSupportFragmentManager().findFragmentByTag(DisplayHelpDialog.TAG));
        }
    }
}
