package com.cappielloantonio.tempo.util;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.cappielloantonio.tempo.App;

/**
 * Singleton that manages a sleep timer countdown.
 *
 * <h3>Rotation survival</h3>
 * The timer survives fragment recreation (e.g. rotation) because it lives
 * in a singleton. Callers reconnect their tick/expiry logic by calling
 * {@link #setTickListener} on resume and clearing it on stop.
 *
 * <h3>Process-death survival</h3>
 * {@link #startTimer} and {@link #startEndOfTrack} persist their state to
 * {@link SharedPreferences} via {@link App#getInstance()}.  When Android
 * kills the process and the singleton is re-created, the constructor
 * restores whatever was saved and, for a countdown timer, resumes the
 * in-process tick loop from the correct wall-clock end time.
 *
 * <h3>End-of-track mode</h3>
 * {@link #startEndOfTrack()} arms a one-shot stop that fires the next time
 * the caller invokes {@link #notifyTrackEnded()}.  This is deliberately
 * driven from the outside (the UI layer owns the player listener) so that
 * this class stays free of Android-framework player dependencies.
 */
public class SleepTimerManager {

    public interface TickListener {
        /**
         * Called on the main thread every second while a countdown is
         * active (expired=false), once more when the countdown reaches zero
         * (expired=true), and once when end-of-track fires (expired=true).
         */
        void onTick(boolean expired);
    }

    // SharedPreferences keys
    private static final String PREF_END_TIME_MS = "sleep_timer_end_time_ms";
    private static final String PREF_END_OF_TRACK = "sleep_timer_end_of_track";

    private static SleepTimerManager instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable scheduledTick;

    private long endTimeMs = 0;
    private boolean active = false;
    private boolean endOfTrack = false;

    private TickListener tickListener;

    private SleepTimerManager() {
        restoreFromPreferences();
    }

    public static SleepTimerManager getInstance() {
        if (instance == null) {
            instance = new SleepTimerManager();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Start (or restart) the timer for the given number of minutes. */
    public void startTimer(int minutes) {
        cancelInternal(false);
        endOfTrack = false;
        endTimeMs = System.currentTimeMillis() + (long) minutes * 60 * 1000;
        active = true;
        persistState();
        scheduleNextTick();
    }

    /**
     * Arm "stop after this song" mode.  The timer fires the next time the
     * caller invokes {@link #notifyTrackEnded()}.
     */
    public void startEndOfTrack() {
        cancelInternal(false);
        endOfTrack = true;
        active = true;
        endTimeMs = 0;
        persistState();
        // Notify immediately so the UI can reflect the active state.
        if (tickListener != null) tickListener.onTick(false);
    }

    /**
     * Cancel the timer and notify the listener so the UI resets.
     * Safe to call even when no timer is running.
     */
    public void cancelTimer() {
        cancelInternal(true);
    }

    /** Whether a countdown or end-of-track timer is currently armed. */
    public boolean isActive() {
        return active;
    }

    /** Whether the active timer is in end-of-track (not countdown) mode. */
    public boolean isEndOfTrack() {
        return endOfTrack;
    }

    /**
     * Remaining countdown time formatted as "MM:SS".
     * Returns an empty string when inactive or in end-of-track mode.
     */
    public String getRemainingFormatted() {
        if (!active || endOfTrack) return "";
        long ms = getRemainingMs();
        long minutes = ms / 60_000;
        long seconds = (ms % 60_000) / 1000;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Attach a listener that receives ticks and the expiry event.
     * Pass {@code null} to disconnect (do this in onStop to avoid leaks).
     * Immediately fires {@link TickListener#onTick(boolean)} with the current
     * state so the UI can sync right away.
     */
    public void setTickListener(TickListener listener) {
        this.tickListener = listener;
        if (listener != null) listener.onTick(false);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private long getRemainingMs() {
        if (!active || endOfTrack) return 0;
        return Math.max(0, endTimeMs - System.currentTimeMillis());
    }

    private void cancelInternal(boolean notifyListener) {
        active = false;
        endOfTrack = false;
        endTimeMs = 0;
        if (scheduledTick != null) {
            handler.removeCallbacks(scheduledTick);
            scheduledTick = null;
        }
        clearPersistedState();
        if (notifyListener && tickListener != null) {
            // expired=false: player keeps playing after a manual cancel.
            tickListener.onTick(false);
        }
    }

    private void scheduleNextTick() {
        scheduledTick = () -> {
            if (!active || endOfTrack) return;

            long remaining = getRemainingMs();
            if (remaining <= 0) {
                active = false;
                scheduledTick = null;
                clearPersistedState();
                if (tickListener != null) tickListener.onTick(true);
            } else {
                if (tickListener != null) tickListener.onTick(false);
                scheduleNextTick();
            }
        };
        handler.postDelayed(scheduledTick, 1000);
    }

    // -------------------------------------------------------------------------
    // Persistence (process-death survival)
    // -------------------------------------------------------------------------

    private void persistState() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        prefs.edit()
                .putLong(PREF_END_TIME_MS, endTimeMs)
                .putBoolean(PREF_END_OF_TRACK, endOfTrack)
                .apply();
    }

    private void clearPersistedState() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        prefs.edit()
                .remove(PREF_END_TIME_MS)
                .remove(PREF_END_OF_TRACK)
                .apply();
    }

    /**
     * Called once from the constructor.  Restores any timer that was active
     * before the process was killed and resumes the countdown if necessary.
     */
    private void restoreFromPreferences() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;

        boolean savedEndOfTrack = prefs.getBoolean(PREF_END_OF_TRACK, false);
        long savedEndTime = prefs.getLong(PREF_END_TIME_MS, 0);

        if (savedEndOfTrack) {
            // Restore end-of-track mode — no ticking, just re-arm the flag.
            endOfTrack = true;
            active = true;
        } else if (savedEndTime > System.currentTimeMillis()) {
            // Restore countdown: the end time is still in the future.
            endTimeMs = savedEndTime;
            active = true;
            scheduleNextTick();
        } else {
            // Stale data (e.g. timer expired while process was dead).
            clearPersistedState();
        }
    }

    private SharedPreferences getPrefs() {
        try {
            return App.getInstance().getPreferences();
        } catch (Exception e) {
            return null;
        }
    }
}
