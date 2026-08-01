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
    private static final float MIN_SPEED = 0.25f;
    private static final float MAX_SPEED = 2.0f;
    private static final float STEP_SIZE = 0.05f;
    private static final float DEFAULT_SPEED = 1.0f;
    private static final float MIN_SLIDER_VALUE = 0.5f;
    private static final float MAX_SLIDER_VALUE = 1.5f;
    private static final float MIN_PITCH = 0.25f;
    private static final float MAX_PITCH = 2.0f;
    private static final float PITCH_STEP_SIZE = 0.05f;
    private static final float DEFAULT_PITCH = 1.0f;

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
        Button playbackSpeedDecreaseButton = view.findViewById(R.id.playback_speed_decrease_button);
        Slider playbackSpeedSlider = view.findViewById(R.id.playback_speed_slider);
        Button playbackSpeedIncreaseButton = view.findViewById(R.id.playback_speed_increase_button);
        MaterialSwitch playbackSpeedPitchSwitch = view.findViewById(R.id.playback_speed_pitch_switch);
        MaterialSwitch playbackSpeedManualPitchSwitch = view.findViewById(R.id.playback_speed_manual_pitch_switch);
        View playbackSpeedManualPitchContainer = view.findViewById(R.id.playback_speed_manual_pitch_container);
        TextView playbackSpeedManualPitchValueTextView = view
                .findViewById(R.id.playback_speed_manual_pitch_value_text_view);
        Button playbackSpeedManualPitchDecreaseButton = view
                .findViewById(R.id.playback_speed_manual_pitch_decrease_button);
        Slider playbackSpeedManualPitchSlider = view.findViewById(R.id.playback_speed_manual_pitch_slider);
        Button playbackSpeedManualPitchIncreaseButton = view
                .findViewById(R.id.playback_speed_manual_pitch_increase_button);
        final boolean[] isResetting = { false };
        final Button[] resetButton = { null };

        float currentSpeed = normalizeSpeed(Preferences.getPlaybackSpeed());
        float currentPitch = normalizePitch(Preferences.getPlaybackSpeedManualPitch());
        playbackSpeedPitchSwitch.setChecked(Preferences.isPlaybackSpeedPitchEnabled());
        playbackSpeedManualPitchSwitch.setChecked(Preferences.isPlaybackSpeedManualPitchEnabled());
        playbackSpeedSlider.setValueFrom(MIN_SLIDER_VALUE);
        playbackSpeedSlider.setValueTo(MAX_SLIDER_VALUE);
        playbackSpeedSlider.setStepSize(0.0f);
        playbackSpeedSlider.setLabelFormatter(value -> getString(
                R.string.player_playback_speed,
                normalizeSpeed(getPlaybackValue(value, MIN_SPEED, MAX_SPEED))));
        playbackSpeedSlider.setValue(getSliderValue(currentSpeed, MIN_SPEED, MAX_SPEED));
        playbackSpeedManualPitchSlider.setValueFrom(MIN_SLIDER_VALUE);
        playbackSpeedManualPitchSlider.setValueTo(MAX_SLIDER_VALUE);
        playbackSpeedManualPitchSlider.setStepSize(0.0f);
        playbackSpeedManualPitchSlider.setLabelFormatter(value -> getString(
                R.string.playback_speed_manual_pitch_value,
                normalizePitch(getPlaybackValue(value, MIN_PITCH, MAX_PITCH)) * 100.0f));
        playbackSpeedManualPitchSlider.setValue(getSliderValue(currentPitch, MIN_PITCH, MAX_PITCH));
        updateSpeedLabel(playbackSpeedValueTextView, currentSpeed);
        updatePitchLabel(playbackSpeedManualPitchValueTextView, currentPitch);
        updateAdjustmentButtonState(playbackSpeedDecreaseButton, playbackSpeedIncreaseButton, currentSpeed, MIN_SPEED,
                MAX_SPEED);
        updateAdjustmentButtonState(
                playbackSpeedManualPitchDecreaseButton,
                playbackSpeedManualPitchIncreaseButton,
                currentPitch,
                MIN_PITCH,
                MAX_PITCH);
        updateManualPitchVisibility(
                playbackSpeedManualPitchSwitch,
                playbackSpeedManualPitchContainer,
                playbackSpeedPitchSwitch.isChecked(),
                playbackSpeedManualPitchSwitch.isChecked());

        playbackSpeedSlider.addOnChangeListener((slider, value, fromUser) -> {
            float speed = normalizeSpeed(getPlaybackValue(value, MIN_SPEED, MAX_SPEED));
            updateSpeedLabel(playbackSpeedValueTextView, speed);
            updateAdjustmentButtonState(playbackSpeedDecreaseButton, playbackSpeedIncreaseButton, speed, MIN_SPEED,
                    MAX_SPEED);
            updateResetButtonState(
                    resetButton[0],
                    speed,
                    playbackSpeedPitchSwitch.isChecked(),
                    playbackSpeedManualPitchSwitch.isChecked(),
                    normalizePitch(getPlaybackValue(playbackSpeedManualPitchSlider.getValue(), MIN_PITCH, MAX_PITCH)));

            if (!fromUser) {
                return;
            }

            playbackSpeedSlider.setValue(getSliderValue(speed, MIN_SPEED, MAX_SPEED));
            Preferences.setPlaybackSpeed(speed);
            if (listener != null) {
                listener.onSpeedSelected(speed);
            }
        });

        playbackSpeedDecreaseButton.setOnClickListener(v -> updateSpeed(
                playbackSpeedSlider,
                playbackSpeedValueTextView,
                playbackSpeedDecreaseButton,
                playbackSpeedIncreaseButton,
                -STEP_SIZE));

        playbackSpeedIncreaseButton.setOnClickListener(v -> updateSpeed(
                playbackSpeedSlider,
                playbackSpeedValueTextView,
                playbackSpeedDecreaseButton,
                playbackSpeedIncreaseButton,
                STEP_SIZE));

        playbackSpeedPitchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Preferences.setPlaybackSpeedPitchEnabled(isChecked);
            updateManualPitchVisibility(
                    playbackSpeedManualPitchSwitch,
                    playbackSpeedManualPitchContainer,
                    isChecked,
                    playbackSpeedManualPitchSwitch.isChecked());
            updateResetButtonState(
                    resetButton[0],
                    normalizeSpeed(getPlaybackValue(playbackSpeedSlider.getValue(), MIN_SPEED, MAX_SPEED)),
                    isChecked,
                    playbackSpeedManualPitchSwitch.isChecked(),
                    normalizePitch(getPlaybackValue(playbackSpeedManualPitchSlider.getValue(), MIN_PITCH, MAX_PITCH)));
            if (isResetting[0]) {
                return;
            }
            if (listener != null) {
                listener.onSpeedSelected(normalizeSpeed(getPlaybackValue(
                        playbackSpeedSlider.getValue(),
                        MIN_SPEED,
                        MAX_SPEED)));
            }
        });

        playbackSpeedManualPitchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Preferences.setPlaybackSpeedManualPitchEnabled(isChecked);
            updateManualPitchVisibility(
                    playbackSpeedManualPitchSwitch,
                    playbackSpeedManualPitchContainer,
                    playbackSpeedPitchSwitch.isChecked(),
                    isChecked);
            updateResetButtonState(
                    resetButton[0],
                    normalizeSpeed(getPlaybackValue(playbackSpeedSlider.getValue(), MIN_SPEED, MAX_SPEED)),
                    playbackSpeedPitchSwitch.isChecked(),
                    isChecked,
                    normalizePitch(getPlaybackValue(playbackSpeedManualPitchSlider.getValue(), MIN_PITCH, MAX_PITCH)));
            if (isResetting[0]) {
                return;
            }
            if (listener != null) {
                listener.onSpeedSelected(normalizeSpeed(getPlaybackValue(
                        playbackSpeedSlider.getValue(),
                        MIN_SPEED,
                        MAX_SPEED)));
            }
        });

        playbackSpeedManualPitchSlider.addOnChangeListener((slider, value, fromUser) -> {
            float pitch = normalizePitch(getPlaybackValue(value, MIN_PITCH, MAX_PITCH));
            updatePitchLabel(playbackSpeedManualPitchValueTextView, pitch);
            updateAdjustmentButtonState(
                    playbackSpeedManualPitchDecreaseButton,
                    playbackSpeedManualPitchIncreaseButton,
                    pitch,
                    MIN_PITCH,
                    MAX_PITCH);
            updateResetButtonState(
                    resetButton[0],
                    normalizeSpeed(getPlaybackValue(playbackSpeedSlider.getValue(), MIN_SPEED, MAX_SPEED)),
                    playbackSpeedPitchSwitch.isChecked(),
                    playbackSpeedManualPitchSwitch.isChecked(),
                    pitch);

            if (!fromUser) {
                return;
            }

            playbackSpeedManualPitchSlider.setValue(getSliderValue(pitch, MIN_PITCH, MAX_PITCH));
            Preferences.setPlaybackSpeedManualPitch(pitch);
            if (listener != null) {
                listener.onSpeedSelected(normalizeSpeed(getPlaybackValue(
                        playbackSpeedSlider.getValue(),
                        MIN_SPEED,
                        MAX_SPEED)));
            }
        });

        playbackSpeedManualPitchDecreaseButton.setOnClickListener(v -> updateManualPitch(
                playbackSpeedSlider,
                playbackSpeedManualPitchSlider,
                playbackSpeedManualPitchValueTextView,
                playbackSpeedManualPitchDecreaseButton,
                playbackSpeedManualPitchIncreaseButton,
                -PITCH_STEP_SIZE));

        playbackSpeedManualPitchIncreaseButton.setOnClickListener(v -> updateManualPitch(
                playbackSpeedSlider,
                playbackSpeedManualPitchSlider,
                playbackSpeedManualPitchValueTextView,
                playbackSpeedManualPitchDecreaseButton,
                playbackSpeedManualPitchIncreaseButton,
                PITCH_STEP_SIZE));

        Dialog dialog = new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.playback_speed_dialog_title)
                .setView(view)
                .setNeutralButton(R.string.playback_speed_dialog_reset_button, null)
                .setNegativeButton(R.string.playback_speed_dialog_negative_button,
                        (dialogInterface, id) -> dialogInterface.cancel())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            resetButton[0] = dialog.findViewById(android.R.id.button3);
            updateResetButtonState(resetButton[0], normalizeSpeed(getPlaybackValue(
                    playbackSpeedSlider.getValue(),
                    MIN_SPEED,
                    MAX_SPEED)),
                    playbackSpeedPitchSwitch.isChecked(),
                    playbackSpeedManualPitchSwitch.isChecked(),
                    normalizePitch(getPlaybackValue(playbackSpeedManualPitchSlider.getValue(), MIN_PITCH, MAX_PITCH)));

            playbackSpeedSlider.addOnChangeListener(
                    (slider, value, fromUser) -> updateResetButtonState(resetButton[0], normalizeSpeed(getPlaybackValue(
                            value,
                            MIN_SPEED,
                            MAX_SPEED)),
                            playbackSpeedPitchSwitch.isChecked(),
                            playbackSpeedManualPitchSwitch.isChecked(),
                            normalizePitch(getPlaybackValue(
                                    playbackSpeedManualPitchSlider.getValue(),
                                    MIN_PITCH,
                                    MAX_PITCH))));

            resetButton[0].setOnClickListener(v -> {
                isResetting[0] = true;
                playbackSpeedSlider.setValue(getSliderValue(DEFAULT_SPEED, MIN_SPEED, MAX_SPEED));
                playbackSpeedPitchSwitch.setChecked(false);
                playbackSpeedManualPitchSwitch.setChecked(false);
                playbackSpeedManualPitchSlider.setValue(getSliderValue(DEFAULT_PITCH, MIN_PITCH, MAX_PITCH));
                isResetting[0] = false;
                updateSpeedLabel(playbackSpeedValueTextView, DEFAULT_SPEED);
                updatePitchLabel(playbackSpeedManualPitchValueTextView, DEFAULT_PITCH);
                Preferences.setPlaybackSpeed(DEFAULT_SPEED);
                Preferences.setPlaybackSpeedManualPitch(DEFAULT_PITCH);
                updateAdjustmentButtonState(
                        playbackSpeedDecreaseButton,
                        playbackSpeedIncreaseButton,
                        DEFAULT_SPEED,
                        MIN_SPEED,
                        MAX_SPEED);
                updateAdjustmentButtonState(
                        playbackSpeedManualPitchDecreaseButton,
                        playbackSpeedManualPitchIncreaseButton,
                        DEFAULT_PITCH,
                        MIN_PITCH,
                        MAX_PITCH);
                updateManualPitchVisibility(
                        playbackSpeedManualPitchSwitch,
                        playbackSpeedManualPitchContainer,
                        false,
                        false);
                updateResetButtonState(resetButton[0], DEFAULT_SPEED, false, false, DEFAULT_PITCH);
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

    private void updatePitchLabel(TextView textView, float pitch) {
        textView.setText(getString(R.string.playback_speed_manual_pitch_value, pitch * 100.0f));
    }

    private void updateManualPitchVisibility(
            MaterialSwitch manualPitchSwitch,
            View manualPitchContainer,
            boolean isPitchEnabled,
            boolean isManualPitchEnabled) {
        manualPitchSwitch.setVisibility(isPitchEnabled ? View.VISIBLE : View.GONE);
        manualPitchContainer.setVisibility(isPitchEnabled && isManualPitchEnabled ? View.VISIBLE : View.GONE);
    }

    private void updateResetButtonState(
            Button resetButton,
            float speed,
            boolean isPitchEnabled,
            boolean isManualPitchEnabled,
            float pitch) {
        if (resetButton == null) {
            return;
        }

        resetButton.setEnabled(
                speed != DEFAULT_SPEED
                        || isPitchEnabled
                        || isManualPitchEnabled
                        || pitch != DEFAULT_PITCH);
    }

    private void updateAdjustmentButtonState(Button decreaseButton, Button increaseButton, float value, float minValue,
            float maxValue) {
        decreaseButton.setEnabled(value > minValue);
        increaseButton.setEnabled(value < maxValue);
    }

    private void updateSpeed(
            Slider slider,
            TextView textView,
            Button decreaseButton,
            Button increaseButton,
            float delta) {
        float speed = normalizeSpeed(getPlaybackValue(slider.getValue(), MIN_SPEED, MAX_SPEED) + delta);

        slider.setValue(getSliderValue(speed, MIN_SPEED, MAX_SPEED));
        updateSpeedLabel(textView, speed);
        updateAdjustmentButtonState(decreaseButton, increaseButton, speed, MIN_SPEED, MAX_SPEED);
        Preferences.setPlaybackSpeed(speed);
        if (listener != null) {
            listener.onSpeedSelected(speed);
        }
    }

    private void updateManualPitch(
            Slider playbackSpeedSlider,
            Slider manualPitchSlider,
            TextView textView,
            Button decreaseButton,
            Button increaseButton,
            float delta) {
        float pitch = normalizePitch(getPlaybackValue(manualPitchSlider.getValue(), MIN_PITCH, MAX_PITCH) + delta);

        manualPitchSlider.setValue(getSliderValue(pitch, MIN_PITCH, MAX_PITCH));
        updatePitchLabel(textView, pitch);
        updateAdjustmentButtonState(decreaseButton, increaseButton, pitch, MIN_PITCH, MAX_PITCH);
        Preferences.setPlaybackSpeedManualPitch(pitch);
        if (listener != null) {
            listener.onSpeedSelected(normalizeSpeed(getPlaybackValue(
                    playbackSpeedSlider.getValue(),
                    MIN_SPEED,
                    MAX_SPEED)));
        }
    }

    private float normalizeSpeed(float speed) {
        return normalizeValue(speed, MIN_SPEED, MAX_SPEED, STEP_SIZE);
    }

    private float normalizePitch(float pitch) {
        return normalizeValue(pitch, MIN_PITCH, MAX_PITCH, PITCH_STEP_SIZE);
    }

    private float normalizeValue(float value, float minValue, float maxValue, float stepSize) {
        float clampedValue = Math.max(minValue, Math.min(maxValue, value));
        return Math.round(clampedValue / stepSize) * stepSize;
    }

    private float getSliderValue(float playbackValue, float minValue, float maxValue) {
        float clampedValue = Math.max(minValue, Math.min(maxValue, playbackValue));

        if (clampedValue <= DEFAULT_SPEED) {
            return MIN_SLIDER_VALUE
                    + ((clampedValue - minValue) / (DEFAULT_SPEED - minValue))
                            * (DEFAULT_SPEED - MIN_SLIDER_VALUE);
        }

        return DEFAULT_SPEED
                + ((clampedValue - DEFAULT_SPEED) / (maxValue - DEFAULT_SPEED))
                        * (MAX_SLIDER_VALUE - DEFAULT_SPEED);
    }

    private float getPlaybackValue(float sliderValue, float minValue, float maxValue) {
        if (sliderValue <= DEFAULT_SPEED) {
            return minValue
                    + ((sliderValue - MIN_SLIDER_VALUE) / (DEFAULT_SPEED - MIN_SLIDER_VALUE))
                            * (DEFAULT_SPEED - minValue);
        }

        return DEFAULT_SPEED
                + ((sliderValue - DEFAULT_SPEED) / (MAX_SLIDER_VALUE - DEFAULT_SPEED))
                        * (maxValue - DEFAULT_SPEED);
    }
}
