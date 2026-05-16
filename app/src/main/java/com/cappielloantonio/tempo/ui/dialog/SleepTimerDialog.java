package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.service.SleepTimerManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Dialog that lets the user choose a sleep-timer duration — similar to the sleep timer in
 * YouTube Music.  Options include fixed durations and an "end of current song" mode.
 */
public class SleepTimerDialog extends DialogFragment {

    private static final String TAG = "SleepTimerDialog";

    // -1 marks the "end of song" entry; 0 marks the "cancel timer" entry.
    private static final long[] DURATION_VALUES_MS = {
            TimeUnit.MINUTES.toMillis(5),
            TimeUnit.MINUTES.toMillis(10),
            TimeUnit.MINUTES.toMillis(15),
            TimeUnit.MINUTES.toMillis(30),
            TimeUnit.MINUTES.toMillis(45),
            TimeUnit.MINUTES.toMillis(60),
            -1L,  // End of song
            0L    // Cancel timer
    };

    public interface SleepTimerListener {
        /** Called when the user selects a fixed-duration option. */
        void onSleepTimerSet(long durationMs);

        /** Called when the user selects "End of song". */
        void onSleepTimerEndOfSong();

        /** Called when the user cancels / clears the current timer. */
        void onSleepTimerCancelled();
    }

    private SleepTimerListener listener;

    public void setSleepTimerListener(SleepTimerListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] labels = buildLabels();
        int selectedIndex = findCurrentSelection();

        return new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.sleep_timer_dialog_title)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    long value = DURATION_VALUES_MS[which];
                    if (value > 0) {
                        if (listener != null) listener.onSleepTimerSet(value);
                    } else if (value == -1L) {
                        if (listener != null) listener.onSleepTimerEndOfSong();
                    } else {
                        if (listener != null) listener.onSleepTimerCancelled();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.sleep_timer_dialog_negative_button,
                        (dialog, id) -> dialog.cancel())
                .create();
    }

    private String[] buildLabels() {
        return new String[]{
                getString(R.string.sleep_timer_5_min),
                getString(R.string.sleep_timer_10_min),
                getString(R.string.sleep_timer_15_min),
                getString(R.string.sleep_timer_30_min),
                getString(R.string.sleep_timer_45_min),
                getString(R.string.sleep_timer_60_min),
                getString(R.string.sleep_timer_end_of_song),
                getString(R.string.sleep_timer_cancel)
        };
    }

    /** Returns the index matching the current timer state, or -1 for no pre-selection. */
    private int findCurrentSelection() {
        SleepTimerManager mgr = SleepTimerManager.instance;
        if (!mgr.isActive()) return -1;
        if (mgr.isEndOfSong()) return 6; // "End of song"
        long remaining = mgr.getRemainingMs();
        for (int i = 0; i < DURATION_VALUES_MS.length; i++) {
            if (DURATION_VALUES_MS[i] > 0 && Math.abs(DURATION_VALUES_MS[i] - remaining) < TimeUnit.MINUTES.toMillis(1)) {
                return i;
            }
        }
        return -1;
    }
}
