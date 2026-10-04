package com.eddyizm.tempus.lan

/** Immutable receiver snapshot. Position advances locally between receiver updates. */
data class LanRemoteState(
    val active: Boolean = false,
    val connected: Boolean = false,
    val name: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverId: String = "",
    val position: Long = 0,
    val duration: Long = 0,
    val playing: Boolean = false,
    val playWhenReady: Boolean = false,
    val volume: Float = 1f,
    val deviceVolume: Int = 0,
    val deviceVolumeMax: Int = 0,
    val index: Int = -1,
    val count: Int = 0,
    val receivedAt: Long = 0,
    val error: Int = 0,
    val revision: String = "",
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val mediaId: String = "",
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val timerActive: Boolean = false,
    val timerEndOfTrack: Boolean = false,
    val timerRemaining: String = "",
) {
    fun positionAt(now: Long): Long = (position + if (connected && playing) ((now - receivedAt).coerceAtLeast(0) * speed.toDouble()).toLong() else 0)
        .coerceIn(0, duration.coerceAtLeast(0))
}
