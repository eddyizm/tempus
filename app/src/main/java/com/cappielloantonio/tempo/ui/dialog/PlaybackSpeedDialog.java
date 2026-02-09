package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PlaybackSpeedDialog extends DialogFragment {
    private static final String TAG = "PlaybackSpeedDialog";

    public interface PlaybackSpeedListener {
        void onSpeedSelected(float speed);
    }

    private PlaybackSpeedListener listener;

    private static final float[] SPEED_VALUES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x"};

    public void setPlaybackSpeedListener(PlaybackSpeedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        float currentSpeed = Preferences.getPlaybackSpeed();
        int selectedIndex = getSelectedIndex(currentSpeed);

        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.playback_speed_dialog_title)
                .setSingleChoiceItems(SPEED_LABELS, selectedIndex, (dialog, which) -> {
                    float selectedSpeed = SPEED_VALUES[which];
                    Preferences.setPlaybackSpeed(selectedSpeed);
                    if (listener != null) {
                        listener.onSpeedSelected(selectedSpeed);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.playback_speed_dialog_negative_button, (dialog, id) -> dialog.cancel())
                .create();
    }

    private int getSelectedIndex(float currentSpeed) {
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            if (Math.abs(SPEED_VALUES[i] - currentSpeed) < 0.01f) {
                return i;
            }
        }
        return 2; // Default to 1.0x
    }
}
