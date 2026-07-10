package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class PlaybackSpeedDialog extends DialogFragment {
    private static final String TAG = "PlaybackSpeedDialog";
    private static final float MIN_SPEED = 0.5f;
    private static final float MAX_SPEED = 2.0f;
    private static final float STEP_SIZE = 0.05f;
    private static final float DEFAULT_SPEED = 1.0f;

    public interface PlaybackSpeedListener {
        void onSpeedSelected(float speed);
    }

    private PlaybackSpeedListener listener;

    public void setPlaybackSpeedListener(PlaybackSpeedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_playback_speed, null);
        TextView playbackSpeedValueTextView = view.findViewById(R.id.playback_speed_value_text_view);
        Slider playbackSpeedSlider = view.findViewById(R.id.playback_speed_slider);
        MaterialSwitch playbackSpeedPitchSwitch = view.findViewById(R.id.playback_speed_pitch_switch);

        float currentSpeed = normalizeSpeed(Preferences.getPlaybackSpeed());
        playbackSpeedPitchSwitch.setChecked(Preferences.isPlaybackSpeedPitchEnabled());
        playbackSpeedSlider.setValueFrom(MIN_SPEED);
        playbackSpeedSlider.setValueTo(MAX_SPEED);
        playbackSpeedSlider.setStepSize(STEP_SIZE);
        playbackSpeedSlider.setValue(currentSpeed);
        updateSpeedLabel(playbackSpeedValueTextView, currentSpeed);

        playbackSpeedSlider.addOnChangeListener((slider, value, fromUser) -> {
            float speed = normalizeSpeed(value);
            updateSpeedLabel(playbackSpeedValueTextView, speed);

            if (!fromUser) {
                return;
            }

            Preferences.setPlaybackSpeed(speed);
            if (listener != null) {
                listener.onSpeedSelected(speed);
            }
        });

        playbackSpeedPitchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Preferences.setPlaybackSpeedPitchEnabled(isChecked);
            if (listener != null) {
                listener.onSpeedSelected(normalizeSpeed(playbackSpeedSlider.getValue()));
            }
        });

        Dialog dialog = new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.playback_speed_dialog_title)
                .setView(view)
                .setNeutralButton(R.string.playback_speed_dialog_reset_button, null)
                .setNegativeButton(R.string.playback_speed_dialog_negative_button,
                        (dialogInterface, id) -> dialogInterface.cancel())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button resetButton = dialog.findViewById(android.R.id.button3);
            updateResetButtonState(resetButton, normalizeSpeed(playbackSpeedSlider.getValue()));

            playbackSpeedSlider.addOnChangeListener(
                    (slider, value, fromUser) -> updateResetButtonState(resetButton, normalizeSpeed(value)));

            resetButton.setOnClickListener(v -> {
                playbackSpeedSlider.setValue(DEFAULT_SPEED);
                updateSpeedLabel(playbackSpeedValueTextView, DEFAULT_SPEED);
                Preferences.setPlaybackSpeed(DEFAULT_SPEED);
                updateResetButtonState(resetButton, DEFAULT_SPEED);
                if (listener != null) {
                    listener.onSpeedSelected(DEFAULT_SPEED);
                }
            });
        });

        return dialog;
    }

    private void updateSpeedLabel(TextView textView, float speed) {
        textView.setText(getString(R.string.player_playback_speed, speed));
    }

    private void updateResetButtonState(Button resetButton, float speed) {
        resetButton.setEnabled(speed != DEFAULT_SPEED);
    }

    private float normalizeSpeed(float speed) {
        float clampedSpeed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        return Math.round(clampedSpeed / STEP_SIZE) * STEP_SIZE;
    }
}
