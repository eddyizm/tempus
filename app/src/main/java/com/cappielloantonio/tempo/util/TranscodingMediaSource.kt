package com.cappielloantonio.tempo.util

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

@OptIn(UnstableApi::class)
class TranscodingMediaSource(
        private val mediaItem: MediaItem,
        private val dataSourceFactory: DataSource.Factory,
        private val progressiveMediaSourceFactory: ProgressiveMediaSource.Factory
) : CompositeMediaSource<Void>() {

    private var durationUs: Long = C.TIME_UNSET
    private var currentSource: MediaSource? = null

    init {
        val extras = mediaItem.mediaMetadata.extras
        val uri = mediaItem.localConfiguration?.uri
        val isLocal = uri?.scheme == "content" || uri?.scheme == "file"

        // Only apply the override if it's NOT a local file
        if (!isLocal && extras != null && extras.containsKey("duration")) {
            val seconds = extras.getInt("duration")
            if (seconds > 0) {
                durationUs = Util.msToUs(seconds * 1000L)
            }
        }

        currentSource = progressiveMediaSourceFactory.createMediaSource(mediaItem)
    }

    override fun getMediaItem() = mediaItem

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val initialSource = progressiveMediaSourceFactory.createMediaSource(mediaItem)
        currentSource = initialSource
        prepareChildSource(null, initialSource)
    }

    override fun onChildSourceInfoRefreshed(
            childSourceId: Void?,
            mediaSource: MediaSource,
            newTimeline: Timeline
    ) {
        val timeline =
                if (durationUs != C.TIME_UNSET) {
                    DurationOverridingTimeline(newTimeline, durationUs)
                } else {
                    newTimeline
                }
        refreshSourceInfo(timeline)
    }

    override fun createPeriod(
            id: MediaSource.MediaPeriodId,
            allocator: Allocator,
            startPositionUs: Long
    ): MediaPeriod {
        val source = currentSource ?: throw IllegalStateException("Source not ready")
        val childPeriod = source.createPeriod(id, allocator, startPositionUs)
        return TranscodingMediaPeriod(childPeriod, source, id, allocator)
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        val transcodingPeriod = mediaPeriod as TranscodingMediaPeriod
        transcodingPeriod.release()
        
        if (transcodingPeriod.currentOffsetUs > 0) {
            releaseChildSource(null)
            val initialSource = progressiveMediaSourceFactory.createMediaSource(mediaItem)
            currentSource = initialSource
            prepareChildSource(null, initialSource)
        }
    }

    override fun getMediaPeriodIdForChildMediaPeriodId(
            childSourceId: Void?,
            mediaPeriodId: MediaSource.MediaPeriodId
    ) = mediaPeriodId

    private inner class TranscodingMediaPeriod(
            private var currentPeriod: MediaPeriod,
            private var source: MediaSource,
            private val id: MediaSource.MediaPeriodId,
            private val allocator: Allocator
    ) : MediaPeriod, MediaPeriod.Callback {

        private var localCallback: MediaPeriod.Callback? = null
        internal var currentOffsetUs: Long = 0
        private var isReloading = false

        private var lastSelections: Array<out ExoTrackSelection?>? = null
        private var lastMayRetainStreamFlags: BooleanArray? = null
        private var activeWrappers: Array<OffsetSampleStream?> = emptyArray()

        fun release() {
            source.releasePeriod(currentPeriod)
        }

        override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
            localCallback = callback
            currentPeriod.prepare(this, positionUs)
        }

        override fun maybeThrowPrepareError() {
            if (!isReloading) currentPeriod.maybeThrowPrepareError()
        }

        override fun getTrackGroups() = currentPeriod.trackGroups

        override fun getStreamKeys(trackSelections: MutableList<ExoTrackSelection>) =
                currentPeriod.getStreamKeys(trackSelections)

        override fun selectTracks(
                selections: Array<out ExoTrackSelection?>,
                mayRetainStreamFlags: BooleanArray,
                streams: Array<SampleStream?>,
                streamResetFlags: BooleanArray,
                positionUs: Long
        ): Long {
            lastSelections = selections
            lastMayRetainStreamFlags = mayRetainStreamFlags

            val childStreams = arrayOfNulls<SampleStream>(streams.size)
            streams.forEachIndexed { i, stream ->
                childStreams[i] = (stream as? OffsetSampleStream)?.childStream
            }

            val startPos =
                    currentPeriod.selectTracks(
                            selections,
                            mayRetainStreamFlags,
                            childStreams,
                            streamResetFlags,
                            positionUs - currentOffsetUs
                    )

            val newWrappers = arrayOfNulls<OffsetSampleStream>(streams.size)
            for (i in streams.indices) {
                val child = childStreams[i]
                if (child == null) {
                    streams[i] = null
                } else {
                    val existingWrapper = streams[i] as? OffsetSampleStream
                    if (existingWrapper != null && existingWrapper.childStream === child) {
                        newWrappers[i] = existingWrapper
                    } else {
                        val wrapper = OffsetSampleStream(child)
                        newWrappers[i] = wrapper
                        streams[i] = wrapper
                    }
                }
            }
            activeWrappers = newWrappers

            return startPos + currentOffsetUs
        }

        override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
            if (!isReloading) {
                currentPeriod.discardBuffer(positionUs - currentOffsetUs, toKeyframe)
            }
        }

        override fun readDiscontinuity(): Long {
            if (isReloading) return C.TIME_UNSET
            val discontinuity = currentPeriod.readDiscontinuity()
            return if (discontinuity == C.TIME_UNSET) C.TIME_UNSET
            else discontinuity + currentOffsetUs
        }

        override fun seekToUs(positionUs: Long): Long {
            if (positionUs == 0L && currentOffsetUs == 0L) {
                return currentPeriod.seekToUs(positionUs)
            }

            reloadSource(positionUs)
            return positionUs
        }

        override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters) =
                positionUs

        override fun getBufferedPositionUs(): Long {
            if (isReloading) return currentOffsetUs
            val buffered = currentPeriod.bufferedPositionUs
            if (buffered == C.TIME_END_OF_SOURCE) return C.TIME_END_OF_SOURCE
            return if (buffered == C.TIME_UNSET) C.TIME_UNSET else buffered + currentOffsetUs
        }

        override fun getNextLoadPositionUs(): Long {
            if (isReloading) return C.TIME_UNSET
            val next = currentPeriod.nextLoadPositionUs
            if (next == C.TIME_END_OF_SOURCE) return C.TIME_END_OF_SOURCE
            return if (next == C.TIME_UNSET) C.TIME_UNSET else next + currentOffsetUs
        }

        override fun reevaluateBuffer(positionUs: Long) {
            if (!isReloading) currentPeriod.reevaluateBuffer(positionUs - currentOffsetUs)
        }

        override fun continueLoading(isLoading: LoadingInfo): Boolean {
            if (isReloading) return false
            val builder = isLoading.buildUpon()
            builder.setPlaybackPositionUs(isLoading.playbackPositionUs - currentOffsetUs)
            return currentPeriod.continueLoading(builder.build())
        }

        override fun isLoading() = isReloading || currentPeriod.isLoading

        override fun onPrepared(mediaPeriod: MediaPeriod) {
            if (isReloading && mediaPeriod == currentPeriod) {
                isReloading = false
                restoreTracks()
                localCallback?.onContinueLoadingRequested(this)
            } else {
                localCallback?.onPrepared(this)
            }
        }

        override fun onContinueLoadingRequested(source: MediaPeriod) {
            if (!isReloading) localCallback?.onContinueLoadingRequested(this)
        }

        private fun reloadSource(positionUs: Long) {
            isReloading = true
            currentOffsetUs = positionUs

            activeWrappers.forEach { it?.childStream = null }

            source.releasePeriod(currentPeriod)
            releaseChildSource(null)

            val seconds = Util.usToMs(positionUs) / 1000
            val newUri = MusicUtil.getStreamUri(mediaItem.mediaId, seconds.toInt())
            val newMediaItem = mediaItem.buildUpon().setUri(newUri).build()

            val newSource = progressiveMediaSourceFactory.createMediaSource(newMediaItem)

            source = newSource
            currentSource = newSource
            prepareChildSource(null, newSource)

            val newPeriod = newSource.createPeriod(id, allocator, 0)
            currentPeriod = newPeriod
            newPeriod.prepare(this, 0)
        }

        private fun restoreTracks() {
            val selections = lastSelections ?: return
            val flags = lastMayRetainStreamFlags ?: return

            val childStreams = arrayOfNulls<SampleStream>(activeWrappers.size)
            val streamResetFlags = BooleanArray(activeWrappers.size)

            currentPeriod.selectTracks(selections, flags, childStreams, streamResetFlags, 0)

            for (i in activeWrappers.indices) {
                activeWrappers[i]?.childStream = childStreams[i]
            }
        }

        private inner class OffsetSampleStream(var childStream: SampleStream?) : SampleStream {
            override fun isReady() = childStream?.isReady ?: false
            override fun maybeThrowError() {
                childStream?.maybeThrowError()
            }

            override fun readData(
                    formatHolder: FormatHolder,
                    buffer: DecoderInputBuffer,
                    readFlags: Int
            ): Int {
                val stream = childStream ?: return C.RESULT_NOTHING_READ
                val result = stream.readData(formatHolder, buffer, readFlags)
                if (result == C.RESULT_BUFFER_READ && !buffer.isEndOfStream) {
                    buffer.timeUs += currentOffsetUs
                }
                return result
            }

            override fun skipData(positionUs: Long) =
                    childStream?.skipData(positionUs - currentOffsetUs) ?: 0
        }
    }

    private class DurationOverridingTimeline(timeline: Timeline, private val durationUs: Long) :
            ForwardingTimeline(timeline) {

        override fun getWindow(
                windowIndex: Int,
                window: Window,
                defaultPositionProjectionUs: Long
        ): Window {
            super.getWindow(windowIndex, window, defaultPositionProjectionUs)
            window.durationUs = durationUs
            window.isSeekable = true
            window.isDynamic = false
            window.liveConfiguration = null
            return window
        }

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            super.getPeriod(periodIndex, period, setIds)
            period.durationUs = durationUs
            return period
        }
    }
}
