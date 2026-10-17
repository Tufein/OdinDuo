package nl.retroid.touchguard;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Advice is independent of protection state; it never changes display routing. */
public final class DisplayHelpDialog extends DialogFragment {
    static final String TAG = "display_help";

    @NonNull @Override public Dialog onCreateDialog(Bundle saved) {
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.screen_help_title)
                .setMessage(R.string.screen_help_body)
                .setPositiveButton(R.string.display_settings,
                        (dialog, which) -> ((MainActivity) requireActivity()).openDisplaySettings())
                .setNeutralButton(R.string.share_diagnostics,
                        (dialog, which) -> ((MainActivity) requireActivity()).shareDiagnostics())
                .setNegativeButton(android.R.string.ok, null).create();
    }
}
