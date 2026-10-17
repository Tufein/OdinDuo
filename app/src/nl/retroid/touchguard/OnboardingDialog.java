package nl.retroid.touchguard;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Optional, resumable introduction. Navigation never enables protection. */
public final class OnboardingDialog extends DialogFragment {
    static final String TAG = "onboarding";
    static final String COMPLETE = "onboarding_complete";
    static final String SKIPPED = "onboarding_skipped";
    private static final String STEP = "onboarding_step";
    private static final int[] TITLES = {R.string.intro_welcome_title, R.string.intro_notifications_title,
            R.string.intro_connect_title, R.string.intro_protection_title};
    private static final int[] BODIES = {R.string.intro_welcome_body, R.string.intro_notifications_body,
            R.string.intro_connect_body, R.string.intro_protection_body};
    private int step;

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
    }

    static boolean pending(Context context) {
        SharedPreferences saved = preferences(context);
        return !saved.getBoolean(COMPLETE, false) && !saved.getBoolean(SKIPPED, false);
    }

    static void open(MainActivity activity, boolean restart) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved() || manager.isDestroyed() || manager.findFragmentByTag(TAG) != null) return;
        if (restart) preferences(activity).edit().remove(STEP).apply();
        new OnboardingDialog().showNow(manager, TAG);
    }

    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        step = Math.max(0, Math.min(TITLES.length - 1,
                saved == null ? preferences(requireContext()).getInt(STEP, 0) : saved.getInt(STEP)));
        View content = getLayoutInflater().inflate(R.layout.dialog_onboarding, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(TITLES[step]).setView(content)
                .setPositiveButton(R.string.intro_next, null)
                .setNegativeButton(R.string.intro_back, null)
                .setNeutralButton(R.string.intro_skip, (ignored, which) -> {
                    preferences(requireContext()).edit().putBoolean(SKIPPED, true).apply();
                }).create();
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (step == TITLES.length - 1) {
                preferences(requireContext()).edit().putBoolean(COMPLETE, true)
                        .remove(SKIPPED).remove(STEP).apply();
                dismiss();
            } else {
                step++;
                saveStep();
                refreshPage();
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> {
            if (step > 0) {
                step--;
                saveStep();
                refreshPage();
            }
        });
        refreshPage();
    }

    private void saveStep() {
        preferences(requireContext()).edit().putInt(STEP, step).apply();
    }

    void refreshPage() {
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null || !dialog.isShowing()) return;
        dialog.setTitle(TITLES[step]);
        ((TextView) dialog.findViewById(R.id.intro_progress)).setText(
                getString(R.string.intro_progress, step + 1, TITLES.length));
        ((TextView) dialog.findViewById(R.id.intro_body)).setText(BODIES[step]);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                step == TITLES.length - 1 ? R.string.intro_finish : R.string.intro_next);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(step == 0 ? View.GONE : View.VISIBLE);
        MaterialButton action = dialog.findViewById(R.id.intro_action);
        action.setVisibility(step == 1 || step == 2 ? View.VISIBLE : View.GONE);
        if (step == 1) {
            boolean allowed = requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            boolean asked = requireContext().getSharedPreferences("appearance", Context.MODE_PRIVATE)
                    .getBoolean("notifications_asked", false);
            action.setText(allowed ? R.string.intro_notifications_allowed
                    : asked ? R.string.app_settings : R.string.intro_allow_notifications);
            action.setEnabled(!allowed);
            action.setOnClickListener(view -> ((MainActivity) requireActivity()).requestNotificationAccess());
        } else if (step == 2) {
            action.setEnabled(true);
            action.setText(R.string.screen_help_title);
            action.setOnClickListener(view -> ((MainActivity) requireActivity()).showDisplayHelp());
        }
        dialog.findViewById(R.id.intro_scroll).scrollTo(0, 0);
    }

    @Override public void onResume() {
        super.onResume();
        refreshPage();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle saved) {
        saved.putInt(STEP, step);
        super.onSaveInstanceState(saved);
    }
}
