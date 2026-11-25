package com.eddyizm.tempus.upnp

/**
 * Whether playback carries on when it comes back from a renderer.
 *
 * Picking the phone or a Bluetooth device in the system output switcher is a switch, and the music keeps
 * playing there. Stopping the cast, in that switcher or in the app's cast dialog, pauses, as when Bluetooth
 * headphones drop. The route provider hears that the renderer was left and the app's MediaRouter hears why,
 * in no fixed order, so whichever comes second decides. Both run on the main thread.
 */
class UpnpHandback(private val windowMs: Long = WINDOW_MS) {

    private var switchedAt = NONE
    private var leftPlayingAt = NONE

    /** The renderer was left. True to keep playing on the phone right away. */
    fun rendererLeft(wasPlaying: Boolean, nowMs: Long): Boolean {
        val switched = switchedAt != NONE && nowMs - switchedAt <= windowMs
        switchedAt = NONE
        leftPlayingAt = if (wasPlaying && !switched) nowMs else NONE
        return wasPlaying && switched
    }

    /** The app's MediaRouter moved off a renderer to another route. True to resume playback now. */
    fun switchedAway(nowMs: Long): Boolean {
        val resume = leftPlayingAt != NONE && nowMs - leftPlayingAt <= windowMs
        leftPlayingAt = NONE
        switchedAt = if (resume) NONE else nowMs
        return resume
    }

    private companion object {
        const val NONE = Long.MIN_VALUE

        // Both arrive within milliseconds of each other. Long enough for a busy main thread, short enough
        // that a stale switch never resumes a later stop.
        const val WINDOW_MS = 3_000L
    }
}
