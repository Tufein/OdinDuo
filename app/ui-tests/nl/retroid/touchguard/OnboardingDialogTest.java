package nl.retroid.touchguard;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.time.Duration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class OnboardingDialogTest {
    @Before public void prepareFirstLaunch() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Settings.Global.putInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 7);
        preferences().edit().clear().commit();
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
                .clear().putBoolean("notifications_asked", false).commit();
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
        GuardPreferences.setAutomatic(context, false);
        context.deleteFile("guard-status.json");
    }

    @Test public void firstLaunchExplainsBeforePermissionAndNavigationNeverStartsProtection() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
            showScheduledIntroduction();
            assertPage(activity, 0);
            assertEquals(View.GONE, onboarding(activity).findViewById(R.id.intro_action).getVisibility());
            for (int page = 0; page < 4; page++) {
                assertPage(activity, page);
                assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
                assertFalse(activity.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                        .getBoolean("notifications_asked", false));
                assertProtectionOff(activity);
                if (page < 3) next(activity);
            }
            assertTrue(OnboardingDialog.pending(activity));
            assertFalse(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
        }
    }

    @Test public void currentPageAndBackNavigationSurviveRecreation() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            next(controller.get());
            next(controller.get());
            assertPage(controller.get(), 2);
            controller.recreate();
            MainActivity activity = controller.get();
            assertPage(activity, 2);
            onboarding(activity).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            assertPage(activity, 1);
            controller.recreate();
            assertPage(controller.get(), 1);
            assertProtectionOff(controller.get());
        }
    }

    @Test public void cancelledIntroductionResumesOnNextLaunchWithoutCompleting() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            next(controller.get());
            next(controller.get());
            onboarding(controller.get()).cancel();
            idle();
            assertNull(controller.get().getSupportFragmentManager().findFragmentByTag(OnboardingDialog.TAG));
            assertTrue(OnboardingDialog.pending(controller.get()));
            assertFalse(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
            assertFalse(preferences().getBoolean(OnboardingDialog.SKIPPED, false));
        }
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            assertPage(controller.get(), 2);
            assertProtectionOff(controller.get());
        }
    }

    @Test public void onlyFinishCompletesIntroductionAndItDoesNotStartProtection() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            for (int page = 0; page < 3; page++) {
                assertFalse(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
                next(controller.get());
            }
            MainActivity activity = controller.get();
            assertPage(activity, 3);
            assertFalse(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
            next(activity);
            idle();
            assertTrue(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
            assertFalse(preferences().getBoolean(OnboardingDialog.SKIPPED, false));
            assertFalse(OnboardingDialog.pending(activity));
            assertNull(activity.getSupportFragmentManager().findFragmentByTag(OnboardingDialog.TAG));
            assertProtectionOff(activity);
            controller.recreate();
            showScheduledIntroduction();
            assertNull(controller.get().getSupportFragmentManager().findFragmentByTag(OnboardingDialog.TAG));
        }
    }

    @Test public void skipSuppressesAutomaticIntroductionWithoutClaimingCompletion() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            next(controller.get());
            onboarding(controller.get()).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            idle();
            assertTrue(preferences().getBoolean(OnboardingDialog.SKIPPED, false));
            assertFalse(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
            assertFalse(OnboardingDialog.pending(controller.get()));
            assertProtectionOff(controller.get());
        }
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            assertNull(controller.get().getSupportFragmentManager().findFragmentByTag(OnboardingDialog.TAG));
            assertProtectionOff(controller.get());
        }
    }

    @Test public void dashboardCanReplaySkippedIntroductionFromTheBeginning() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            next(controller.get());
            next(controller.get());
            onboarding(controller.get()).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            idle();
            MainActivity activity = controller.get();
            assertTrue(activity.findViewById(R.id.onboarding).performClick());
            assertPage(activity, 0);
            assertEquals(View.GONE, onboarding(activity).getButton(AlertDialog.BUTTON_NEGATIVE).getVisibility());
            for (int page = 0; page < 4; page++) next(activity);
            idle();
            assertTrue(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
            assertFalse(preferences().getBoolean(OnboardingDialog.SKIPPED, false));
            assertProtectionOff(activity);
        }
    }

    @Test public void screenHelpIsReachableWithoutProtectionAndSurvivesRecreation() {
        preferences().edit().putBoolean(OnboardingDialog.SKIPPED, true).commit();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity activity = controller.get();
            assertTrue(activity.findViewById(R.id.screen_help).performClick());
            assertTrue(displayHelp(activity).isShowing());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
            assertProtectionOff(activity);
            controller.recreate();
            activity = controller.get();
            showScheduledIntroduction();
            assertTrue(displayHelp(activity).isShowing());
            assertProtectionOff(activity);
            displayHelp(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idle();
            Intent settings = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(settings);
            assertEquals(Settings.ACTION_DISPLAY_SETTINGS, settings.getAction());
            assertProtectionOff(activity);
        }
    }

    @Test @Config(qualifiers = "nl-land") public void dutchLandscapeConnectionPageKeepsItsPlaceAfterScreenHelp() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            MainActivity activity = controller.get();
            assertEquals("nl", activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
            assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE,
                    activity.getResources().getConfiguration().orientation);
            assertPage(activity, 0);
            assertEquals(activity.getString(R.string.intro_next),
                    onboarding(activity).getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
            assertNotEquals(AppLanguage.english(activity).getString(R.string.intro_welcome_body),
                    ((TextView) onboarding(activity).findViewById(R.id.intro_body)).getText().toString());
            next(activity);
            next(activity);
            assertPage(activity, 2);
            assertTrue(onboarding(activity).findViewById(R.id.intro_action).performClick());
            assertTrue(displayHelp(activity).isShowing());
            assertEquals(activity.getString(R.string.screen_help_body),
                    ((TextView) displayHelp(activity).findViewById(android.R.id.message)).getText().toString());
            controller.recreate();
            activity = controller.get();
            assertTrue(displayHelp(activity).isShowing());
            displayHelp(activity).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            idle();
            assertPage(activity, 2);
            assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
            assertProtectionOff(activity);
        }
    }

    @Test public void notificationsRequireExplicitActionAndPermissionSettingsReturnKeepsPage() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            showScheduledIntroduction();
            MainActivity activity = controller.get();
            next(activity);
            assertPage(activity, 1);
            assertNull(Shadows.shadowOf(activity).getLastRequestedPermission());
            assertTrue(onboarding(activity).findViewById(R.id.intro_action).performClick());
            ShadowActivity.PermissionsRequest request = Shadows.shadowOf(activity).getLastRequestedPermission();
            assertNotNull(request);
            assertEquals(1, request.requestCode);
            assertArrayEquals(new String[]{Manifest.permission.POST_NOTIFICATIONS}, request.requestedPermissions);
            assertTrue(activity.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                    .getBoolean("notifications_asked", false));
            controller.recreate();
            activity = controller.get();
            assertPage(activity, 1);
            activity.onRequestPermissionsResult(1, new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    new int[]{PackageManager.PERMISSION_DENIED});
            showScheduledIntroduction();
            assertPage(activity, 1);
            assertTrue(onboarding(activity).findViewById(R.id.intro_action).performClick());
            Intent settings = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(settings);
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.getAction());
            assertEquals("package:" + activity.getPackageName(), settings.getDataString());
            controller.pause().stop().start().resume().visible();
            showScheduledIntroduction();
            assertPage(controller.get(), 1);
            assertFalse(preferences().getBoolean(OnboardingDialog.COMPLETE, false));
            assertProtectionOff(controller.get());
        }
    }

    private static SharedPreferences preferences() {
        return RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
    }

    private static void showScheduledIntroduction() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static AlertDialog onboarding(MainActivity activity) {
        DialogFragment fragment = (DialogFragment) activity.getSupportFragmentManager()
                .findFragmentByTag(OnboardingDialog.TAG);
        assertNotNull("Introduction should be open", fragment);
        AlertDialog dialog = (AlertDialog) fragment.getDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private static AlertDialog displayHelp(MainActivity activity) {
        DialogFragment fragment = (DialogFragment) activity.getSupportFragmentManager()
                .findFragmentByTag(DisplayHelpDialog.TAG);
        assertNotNull("Screen help should be open", fragment);
        return (AlertDialog) fragment.getDialog();
    }

    private static void next(MainActivity activity) {
        assertTrue(onboarding(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
    }

    private static void assertPage(MainActivity activity, int page) {
        assertEquals(activity.getString(R.string.intro_progress, page + 1, 4),
                ((TextView) onboarding(activity).findViewById(R.id.intro_progress)).getText().toString());
        int[] bodies = {R.string.intro_welcome_body, R.string.intro_notifications_body,
                R.string.intro_connect_body, R.string.intro_protection_body};
        assertEquals(activity.getString(bodies[page]),
                ((TextView) onboarding(activity).findViewById(R.id.intro_body)).getText().toString());
    }

    private static void assertProtectionOff(MainActivity activity) {
        assertFalse(GuardPreferences.automatic(activity));
        assertFalse(GuardStatusStore.read(activity).running);
        assertFalse(((MaterialSwitch) activity.findViewById(R.id.automatic)).isChecked());
        assertEquals(activity.getString(R.string.start_protection),
                ((TextView) activity.findViewById(R.id.start)).getText().toString());
        assertTrue(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getAllStartedServices().isEmpty());
    }
}
