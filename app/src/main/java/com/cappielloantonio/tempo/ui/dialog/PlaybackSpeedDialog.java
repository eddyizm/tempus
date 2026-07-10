package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

public class PlaybackSpeedDialog extends DialogFragment {
    private static final String TAG = "PlaybackSpeedDialog";
    private static final float MIN_SPEED = 0.5f;
    private static final float MAX_SPEED = 2.0f;
    private static final float STEP_SIZE = 0.05f;

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

        float currentSpeed = normalizeSpeed(Preferences.getPlaybackSpeed());
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

        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.playback_speed_dialog_title)
                .setView(view)
                .setNegativeButton(R.string.playback_speed_dialog_negative_button, (dialog, id) -> dialog.cancel())
                .create();
    }

    private void updateSpeedLabel(TextView textView, float speed) {
        textView.setText(getString(R.string.player_playback_speed, speed));
    }

    private float normalizeSpeed(float speed) {
        float clampedSpeed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        return Math.round(clampedSpeed / STEP_SIZE) * STEP_SIZE;
    }
}
