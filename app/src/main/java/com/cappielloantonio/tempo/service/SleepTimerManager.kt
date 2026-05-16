package com.cappielloantonio.tempo.service

import android.os.Handler
import android.os.Looper

/**
 * Manages a sleep timer that stops playback after a specified duration or at the end of the
 * current song — similar to the sleep timer feature in YouTube Music.
 *
 * The [scheduler] and [clock] parameters are injectable to make unit testing straightforward
 * without requiring a real [android.os.Looper].
 */
class SleepTimerManager(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val scheduler: Scheduler = HandlerScheduler()
) {

    // ----- Public state -----------------------------------------------------------------------

    /** True when a countdown or end-of-song timer is currently armed. */
    var isActive: Boolean = false
        private set

    /** True when the timer is set to fire at the end of the current song. */
    var isEndOfSong: Boolean = false
        private set

    // ----- Private state ----------------------------------------------------------------------

    private var expiresAtMs: Long = -1L
    private var runnable: Runnable? = null
    private var onExpireCallback: Runnable? = null

    // ----- Public API -------------------------------------------------------------------------

    /**
     * Starts a countdown timer.  Any previously active timer is cancelled first.
     *
     * @param durationMs  Duration in milliseconds before playback is stopped.
     * @param onExpire    Callback invoked on the main thread when the timer fires.
     */
    fun startCountdown(durationMs: Long, onExpire: Runnable) {
        cancelTimer()
        isEndOfSong = false
        expiresAtMs = clock() + durationMs
        onExpireCallback = onExpire
        val r = Runnable {
            expiresAtMs = -1L
            isActive = false
            onExpireCallback?.run()
        }
        runnable = r
        scheduler.schedule(durationMs, r)
        isActive = true
    }

    /**
     * Arms the "end of song" mode.  Playback will stop when [notifyEndOfSong] is called
     * (typically from a [androidx.media3.common.Player.Listener.onPlaybackStateChanged] callback).
     *
     * @param onExpire  Callback invoked when the current song ends.
     */
    fun startEndOfSong(onExpire: Runnable) {
        cancelTimer()
        isEndOfSong = true
        onExpireCallback = onExpire
        isActive = true
    }

    /**
     * Cancels any active timer without triggering the expire callback.
     */
    fun cancelTimer() {
        runnable?.let { scheduler.cancel(it) }
        runnable = null
        expiresAtMs = -1L
        isEndOfSong = false
        isActive = false
        onExpireCallback = null
    }

    /**
     * Returns the number of milliseconds remaining until the timer fires, or -1 if no
     * countdown is active (e.g. end-of-song mode, or no timer set).
     */
    fun getRemainingMs(): Long {
        if (!isActive || isEndOfSong || expiresAtMs < 0) return -1L
        return maxOf(0L, expiresAtMs - clock())
    }

    /**
     * Should be called when the current song reaches its natural end.  If the timer is in
     * end-of-song mode, this triggers the expire callback and resets state.
     */
    fun notifyEndOfSong() {
        if (!isActive || !isEndOfSong) return
        val cb = onExpireCallback
        cancelTimer()
        cb?.run()
    }

    // ----- Scheduler abstraction (for testability) -------------------------------------------

    interface Scheduler {
        fun schedule(delayMs: Long, action: Runnable)
        fun cancel(action: Runnable)
    }

    /** Production scheduler backed by a main-thread [Handler]. */
    class HandlerScheduler : Scheduler {
        // Lazy so that Looper.getMainLooper() is not called at class-load time,
        // which lets pure-JVM unit tests load SleepTimerManager without crashing.
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        override fun schedule(delayMs: Long, action: Runnable) {
            handler.postDelayed(action, delayMs)
        }
        override fun cancel(action: Runnable) {
            handler.removeCallbacks(action)
        }
    }

    // ----- Singleton --------------------------------------------------------------------------

    companion object {
        @JvmField
        val instance: SleepTimerManager = SleepTimerManager()
    }
}
