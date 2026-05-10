package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.SleepTimerManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SleepTimerDialog extends DialogFragment {

    private static final String TAG = "SleepTimerDialog";

    public interface SleepTimerListener {
        void onTimerSet(int minutes);
        void onTimerCancelled();

        /** Called when the user picks "End of track" (stop after current song). */
        default void onEndOfTrackSet() {}
    }

    // Sentinel values — must not collide with real minute counts.
    private static final int END_OF_TRACK = -2;
    private static final int CUSTOM       = -1;

    /**
     * Minute values parallel to {@code R.array.sleep_timer_duration_labels}.
     * END_OF_TRACK and CUSTOM must be the last two entries.
     */
    private static final int[] MINUTE_VALUES = {5, 10, 15, 20, 30, 45, 60, END_OF_TRACK, CUSTOM};

    private SleepTimerListener listener;

    public void setSleepTimerListener(SleepTimerListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Labels come from strings.xml so they are fully localizable.
        String[] labels = getResources().getStringArray(R.array.sleep_timer_duration_labels);

        boolean timerActive = SleepTimerManager.getInstance().isActive();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.sleep_timer_dialog_title)
                .setSingleChoiceItems(labels, -1, (dialog, which) -> {
                    int value = MINUTE_VALUES[which];
                    if (value == CUSTOM) {
                        dialog.dismiss();
                        showCustomInputDialog();
                    } else if (value == END_OF_TRACK) {
                        if (listener != null) listener.onEndOfTrackSet();
                        dialog.dismiss();
                    } else {
                        if (listener != null) listener.onTimerSet(value);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.sleep_timer_dialog_close,
                        (dialog, id) -> dialog.cancel());

        if (timerActive) {
            boolean isEndOfTrack = SleepTimerManager.getInstance().isEndOfTrack();
            String statusMessage;
            if (isEndOfTrack) {
                statusMessage = getString(R.string.sleep_timer_dialog_end_of_track_active);
            } else {
                String remaining = SleepTimerManager.getInstance().getRemainingFormatted();
                statusMessage = getString(R.string.sleep_timer_dialog_active_message, remaining);
            }
            builder.setMessage(statusMessage);
            builder.setNeutralButton(R.string.sleep_timer_dialog_cancel_timer,
                    (dialog, id) -> {
                        if (listener != null) listener.onTimerCancelled();
                    });
        }

        return builder.create();
    }

    private void showCustomInputDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.sleep_timer_custom_hint));
        int dp16 = Math.round(16 * getResources().getDisplayMetrics().density);
        input.setPadding(dp16, dp16, dp16, dp16);

        new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.sleep_timer_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.sleep_timer_custom_set, (d, which) -> {
                    String text = input.getText().toString().trim();
                    if (!text.isEmpty()) {
                        try {
                            int minutes = Integer.parseInt(text);
                            if (minutes > 0 && listener != null) {
                                listener.onTimerSet(minutes);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                })
                .setNegativeButton(R.string.sleep_timer_dialog_close, null)
                .show();
    }
}
