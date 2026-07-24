package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.fragment.app.DialogFragment;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

@OptIn(markerClass = UnstableApi.class)
public abstract class BaseSyncDialog extends DialogFragment {
    private final Runnable onCancel;

    public BaseSyncDialog(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    protected abstract int getDialogTitleResId();
    protected abstract View createDialogView(LayoutInflater layoutInflater);
    protected abstract void performSync(Context context, Runnable onDismiss);
    protected abstract void setSyncPreference(boolean enabled);

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = createDialogView(getLayoutInflater());

        return new MaterialAlertDialogBuilder(getActivity())
                .setView(view)
                .setTitle(getDialogTitleResId())
                .setPositiveButton(R.string.starred_sync_dialog_positive_button, null)
                .setNeutralButton(R.string.starred_sync_dialog_neutral_button, null)
                .setNegativeButton(R.string.starred_sync_dialog_negative_button, null)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();
        setButtonAction(requireContext());
    }

    private void setButtonAction(Context context) {
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) getDialog();

        if (dialog != null) {
            Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> performSync(context, dialog::dismiss));

            Button neutralButton = dialog.getButton(Dialog.BUTTON_NEUTRAL);
            neutralButton.setOnClickListener(v -> {
                setSyncPreference(true);
                dialog.dismiss();
            });

            Button negativeButton = dialog.getButton(Dialog.BUTTON_NEGATIVE);
            negativeButton.setOnClickListener(v -> {
                setSyncPreference(false);
                if (onCancel != null) onCancel.run();
                dialog.dismiss();
            });
        }
    }
}
