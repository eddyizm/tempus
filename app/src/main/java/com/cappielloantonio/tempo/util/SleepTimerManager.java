package com.cappielloantonio.tempo.util;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.Player;

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
 * {@link #startEndOfTrack()} arms a one-shot stop.  Once armed,
 * {@link #armEndOfTrackFadePoller(Player)} polls playback position and
 * triggers a fade-out when the track is about to end.
 *
 * <h3>Fade-out</h3>
 * {@link #startFadeOutThenPause(Player)} and {@link #armEndOfTrackFadePoller(Player)}
 * live here (not in BaseMediaService) so that all sleep-timer logic is
 * consolidated in one place and BaseMediaService stays free of auxiliary
 * sleep-timer state.
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

    /**
     * Listener registered by {@link com.cappielloantonio.tempo.service.BaseMediaService}
     * to drive the fade-out and pause actions from the service process.
     * Receives the same expired signal as {@link TickListener} but is kept
     * separate so UI concerns (tick label refresh) and playback actions
     * (fade, pause) are decoupled.
     */
    public interface ServiceActionListener {
        /** Called every second for countdown updates, and with expired=true when the timer fires. */
        void onTick(boolean expired);
        /**
         * Called immediately when end-of-track mode is armed, so the service can
         * call {@link SleepTimerManager#armEndOfTrackFadePoller(Player)} against the live player.
         */
        void onEndOfTrackArmed();
    }

    // SharedPreferences keys
    private static final String PREF_END_TIME_MS = "sleep_timer_end_time_ms";
    private static final String PREF_END_OF_TRACK = "sleep_timer_end_of_track";

    private static SleepTimerManager instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable scheduledTick;

    /** Duration of the volume fade-out in milliseconds. */
    private static final long FADE_DURATION_MS = 10_000L;
    /** Number of discrete volume steps during the fade. */
    private static final int FADE_STEPS = 40;

    /** Set to true to abort an in-progress fade (e.g. track transition fired early). */
    private volatile boolean abortCurrentFade = false;

    private Runnable endOfTrackPoller;

    private long endTimeMs = 0;
    private boolean active = false;
    private boolean endOfTrack = false;

    private TickListener tickListener;
    private ServiceActionListener serviceActionListener;

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
        if (serviceActionListener != null) serviceActionListener.onEndOfTrackArmed();
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
     * Attach or detach the service-side action listener.
     * Pass {@code null} when the service is destroyed.
     */
    public void setServiceActionListener(ServiceActionListener listener) {
        this.serviceActionListener = listener;
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
    // Fade-out and end-of-track polling
    // -------------------------------------------------------------------------

    /**
     * Gradually lowers the player volume to zero over {@link #FADE_DURATION_MS}, then
     * pauses and restores full volume. Respects {@link #abortCurrentFade}.
     */
    public void startFadeOutThenPause(Player player) {
        long stepMs = FADE_DURATION_MS / FADE_STEPS;
        float decrement = 1f / FADE_STEPS;
        float[] volume = {1f};

        abortCurrentFade = false;

        Runnable fadeStep = new Runnable() {
            @Override
            public void run() {
                if (abortCurrentFade) {
                    player.setVolume(1f);
                    return;
                }
                volume[0] = Math.max(0f, volume[0] - decrement);
                player.setVolume(volume[0]);
                if (volume[0] > 0f) {
                    handler.postDelayed(this, stepMs);
                } else {
                    // Fade complete — cancel the timer and pause.
                    cancelTimer();
                    player.pause();
                    handler.postDelayed(() -> player.setVolume(1f), 300);
                }
            }
        };
        handler.post(fadeStep);
    }

    /**
     * Polls playback position every 500 ms while end-of-track is armed.
     * Kicks off {@link #startFadeOutThenPause(Player)} when {@link #FADE_DURATION_MS}
     * or fewer milliseconds remain on the current track.
     */
    public void armEndOfTrackFadePoller(Player player) {
        stopEndOfTrackPoller();
        abortCurrentFade = false;

        endOfTrackPoller = new Runnable() {
            boolean fadeStarted = false;

            @Override
            public void run() {
                if (!isEndOfTrack()) return;
                if (fadeStarted) return;

                long duration = player.getDuration();
                long position = player.getCurrentPosition();

                if (duration > 0 && duration != C.TIME_UNSET) {
                    long remaining = duration - position;
                    if (remaining >= 1 && remaining <= FADE_DURATION_MS) {
                        fadeStarted = true;
                        startFadeOutThenPause(player);
                        return;
                    }
                }
                handler.postDelayed(this, 500);
            }
        };
        handler.post(endOfTrackPoller);
    }

    /** Cancels any running end-of-track position poller. */
    public void stopEndOfTrackPoller() {
        if (endOfTrackPoller != null) {
            handler.removeCallbacks(endOfTrackPoller);
            endOfTrackPoller = null;
        }
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
        abortCurrentFade = true;
        if (scheduledTick != null) {
            handler.removeCallbacks(scheduledTick);
            scheduledTick = null;
        }
        stopEndOfTrackPoller();
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
                if (serviceActionListener != null) serviceActionListener.onTick(true);
            } else {
                if (tickListener != null) tickListener.onTick(false);
                if (serviceActionListener != null) serviceActionListener.onTick(false);
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
