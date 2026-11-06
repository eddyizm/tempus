package com.eddyizm.tempus.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.media3.common.Player
import java.util.concurrent.atomic.AtomicInteger

object WidgetState {

    // A partial update of the timing or the secondary controls made while a full update is
    // still loading its cover is reapplied onto that full update when it paints. The versions
    // tell which partial updates came after the full update started, the generation drops cover
    // callbacks from an older full update, and the shown cover drives the 10 second cover retry.
    private val fullUpdateGeneration = AtomicInteger()

    @Volatile
    private var shownCoverArtId: String? = null

    private var version = 0
    private var controlsVersionAtStart = 0
    private var timingVersionAtStart = 0

    private var shuffleEnabled = false
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var controlsVersion = 0

    private var positionMs = 0L
    private var durationMs = 0L
    private var timingVersion = 0

    @JvmStatic
    fun version(): Int = version

    fun setSecondaryControls(shuffleEnabled: Boolean, repeatMode: Int) {
        this.shuffleEnabled = shuffleEnabled
        this.repeatMode = repeatMode
        controlsVersion = ++version
    }

    fun setTiming(positionMs: Long, durationMs: Long) {
        this.positionMs = positionMs
        this.durationMs = durationMs
        timingVersion = ++version
    }

    @JvmStatic
    fun beginFullUpdate(controlsVersionAtStart: Int, timingVersionAtStart: Int): Int {
        this.controlsVersionAtStart = controlsVersionAtStart
        this.timingVersionAtStart = timingVersionAtStart
        return fullUpdateGeneration.incrementAndGet()
    }

    @JvmStatic
    fun isCurrent(generation: Int): Boolean = generation == fullUpdateGeneration.get()

    @JvmStatic
    fun applyChangesSinceStart(ctx: Context, rv: RemoteViews) {
        if (controlsVersion > controlsVersionAtStart) {
            WidgetSecondaryControlsViews.apply(ctx, rv, shuffleEnabled, repeatMode)
        }
        if (timingVersion > timingVersionAtStart) {
            val timing = WidgetTimingViews.createTimingInfo(positionMs, durationMs)
            WidgetTimingViews.apply(ctx, rv, timing.elapsedText, timing.totalText, timing.progress)
        }
    }

    @JvmStatic
    fun setShownCover(coverArtId: String) {
        shownCoverArtId = coverArtId
    }

    @JvmStatic
    fun clearShownCover() {
        shownCoverArtId = null
    }

    @JvmStatic
    fun needsCover(coverArtId: String?): Boolean =
        !coverArtId.isNullOrEmpty() && coverArtId != shownCoverArtId
}
