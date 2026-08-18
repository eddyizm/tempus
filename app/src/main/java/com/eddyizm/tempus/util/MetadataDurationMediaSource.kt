package com.eddyizm.tempus.util

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource

/**
 * Fills in a song length the underlying stream cannot report itself.
 *
 * Some servers (e.g. gonic) deliver a transcode they performed as a chunked response with no
 * Content-Length, so ExoPlayer cannot derive the track length and the player screen shows 0:00
 * even though every list view, which reads the Subsonic metadata, shows the correct duration.
 * See issue #577.
 *
 * The metadata duration is substituted only while the stream reports none of its own, and stops
 * being applied as soon as ExoPlayer derives one. Single window progressive sources only: the
 * constructor accepts any MediaSource, but only window 0 is inspected.
 */
@OptIn(UnstableApi::class)
class MetadataDurationMediaSource(childSource: MediaSource) : WrappingMediaSource(childSource) {

    private val overrideDurationUs: Long = run {
        val seconds = childSource.mediaItem.mediaMetadata.extras?.getInt("duration", 0) ?: 0
        if (seconds > 0) Util.msToUs(seconds.toLong() * 1000L) else C.TIME_UNSET
    }

    override fun onChildSourceInfoRefreshed(newTimeline: Timeline) {
        if (newTimeline.windowCount == 0) {
            refreshSourceInfo(newTimeline)
            return
        }

        val window = newTimeline.getWindow(0, Timeline.Window())
        val timeline =
                if (shouldFill(window.durationUs, window.isPlaceholder, overrideDurationUs)) {
                    DurationFillingTimeline(newTimeline, overrideDurationUs)
                } else {
                    newTimeline
                }
        refreshSourceInfo(timeline)
    }

    companion object {
        /**
         * True only for a real timeline that reports no duration of its own, when the metadata does
         * have one.
         *
         * The placeholder timeline a progressive source publishes first has no duration either, and
         * filling that one would briefly apply the metadata duration to every item on every server.
         */
        @JvmStatic
        fun shouldFill(childDurationUs: Long, isPlaceholder: Boolean, overrideDurationUs: Long): Boolean =
                overrideDurationUs != C.TIME_UNSET &&
                        childDurationUs == C.TIME_UNSET &&
                        !isPlaceholder
    }

    /**
     * Fills the window duration and changes nothing else.
     *
     * The period is deliberately left alone. The duration on screen is read from the window, while
     * a pending resume position is clamped against the period, so filling the period as well would
     * let a server declaring a duration shorter than the audio it sends pull a saved position back
     * to the declared end.
     *
     * Not TranscodingMediaSource's DurationOverridingTimeline, which also forces isSeekable: that
     * is correct for a transcode the client requested by offset, and wrong for one the server chose
     * and offers no seeking for.
     */
    private class DurationFillingTimeline(
            timeline: Timeline,
            private val durationUs: Long
    ) : ForwardingTimeline(timeline) {

        override fun getWindow(
                windowIndex: Int,
                window: Window,
                defaultPositionProjectionUs: Long
        ): Window {
            super.getWindow(windowIndex, window, defaultPositionProjectionUs)
            window.durationUs = durationUs
            return window
        }
    }
}
