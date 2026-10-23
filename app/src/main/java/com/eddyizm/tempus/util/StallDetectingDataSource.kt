package com.eddyizm.tempus.util

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * Fails a stream whose connection has slowed to a trickle, so the player reconnects.
 *
 * A TCP connection can get stuck at roughly one segment per round trip (~50 kbps on mobile data)
 * and stay there for minutes while new connections to the same server run at full speed. Data
 * keeps arriving, so no read timeout fires, and playback alternates between a few seconds of audio
 * and long buffering pauses. Skipping the track fixes it, because that opens a new connection.
 *
 * This wrapper does the same thing automatically. It measures throughput over a window sliding
 * along the time spent waiting inside [read] (time the loader spends idle with a full buffer does
 * not count, so the window is 8 s of downloading, not of the clock). Once the rate over a whole
 * window is below what the stream needs to play in real time, it throws [StalledStreamException].
 * `ProgressiveMediaPeriod` treats that as a retryable load error and reopens the stream, normally
 * at the current position with a range request. Give the media source a [RetryPolicy] as well, so
 * these reconnects do not count towards Media3's limit of retries without audio. The new request
 * must also get a new connection: over HTTP/2 it would share the stuck one, so the OkHttp client
 * used for servers with custom headers speaks HTTP/1.1 only (see `DownloadUtil`).
 *
 * A reconnect only helps when the connection was at fault, not the network. So the connection
 * after a reconnect decides: if its first whole window is just as slow, the network is too slow
 * for this stream, and the wrapper stops reconnecting until a whole window runs fast enough again.
 * While the network is too slow, it still tries a reconnect after every minute of slow reading,
 * in case the connection after the reconnect was the one stuck.
 *
 * A reconnect normally resumes where the stalled connection was. Before playback starts it may
 * instead start a large cover picture or other header over, which Media3 reads in one piece; the
 * wrapper sees this as the next connection opening further back, but not before where the stalled
 * one started, and near the start of the file. It then does not reconnect again until the stream
 * is past that point, does not try the minutely reconnects for this song, and after a second such
 * restart leaves the song alone. A seek that lands at that moment can look the same; it is told
 * apart only when it goes further back or the stall was further in.
 *
 * A server that answers the range request with the whole file makes Media3 download and drop
 * everything before the position. Once a resumed connection shows that (no `Content-Range` in the
 * response), the wrapper does not reconnect again for this song.
 *
 * With the streaming cache on, the cache opens the network connection inside [read], so a slow
 * first response counts as slow reading; the reconnect that follows is quick, so this costs a
 * reconnect, not a stall.
 *
 * The rate needed is an average for the whole song, so a variable-bitrate file whose average is
 * below the stuck rate is not helped, even where a passage needs more than the connection gives.
 */
@UnstableApi
class StallDetectingDataSource(
    private val upstream: DataSource,
    /** The song's bitrate in kbps, or 0 if unknown. */
    private val bitrateKbps: Int,
    /** The song's duration in seconds, or 0 if unknown. */
    private val durationSeconds: Int,
    private val windowMs: Long = WINDOW_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : DataSource {

    init {
        require(windowMs > 0) { "windowMs must be positive" }
    }

    /** Running totals of read time and bytes after a read; the window is the span between two. */
    private class Sample(val readMs: Long, val bytes: Long)

    private var watching = false
    private var minBytesPerSecond = 0L
    private var readMs = 0L
    private var bytes = 0L
    private val samples = ArrayDeque<Sample>()

    /**
     * We reconnected and are waiting for a connection to show whether that helped. It stays set
     * across connections that fail or end without showing anything, and is settled by a whole
     * window, by reaching the end of the stream, or by a connection that ran fast enough for a
     * while before it ended (for example because the player seeked).
     */
    private var verdictPending = false
    /** This connection was opened after our reconnect, so it can settle [verdictPending]. */
    private var openedAfterReconnect = false
    /** A reconnect did not help: the network is too slow, so do not reconnect again for now. */
    private var tooSlowForStream = false
    /** Read time over every connection, and when the network was found too slow. */
    private var totalReadMs = 0L
    private var tooSlowSinceMs = 0L

    /** Where in the stream this connection started; where the stalled one started and stopped. */
    private var openPosition = 0L
    private var stalledOpenPosition = 0L
    private var stallPosition = 0L
    private var restartChecked = true
    /** Reconnects that started a header over, and the position before which not to reconnect. */
    private var restarts = 0
    private var noReconnectBefore = 0L
    /** This connection resumed mid-file and its response has not been checked for a range yet. */
    private var rangeUnchecked = false
    /** The server sent the whole file for a range request, so reconnecting costs a full download. */
    private var rangesIgnored = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        readMs = 0
        bytes = 0
        samples.clear()
        samples.addLast(Sample(0, 0))
        watching = false
        openedAfterReconnect = verdictPending
        openPosition = dataSpec.position
        if (!restartChecked) {
            // The first connection after our reconnect shows where the player resumed. A resume
            // repeats a little (the read we failed, data the extractor had only peeked at), but a
            // restart either used nothing the stalled connection sent, or goes back much further.
            restartChecked = true
            val position = dataSpec.position
            if (position >= stalledOpenPosition && stallPosition <= HEADER_BYTES &&
                (position == stalledOpenPosition || position < stallPosition - UNUSED_BYTES)) {
                restarts++
                noReconnectBefore = stallPosition
            }
        }
        val length = upstream.open(dataSpec)
        // Without a known length the player may have to start the stream over to retry it, so such
        // streams are left alone.
        watching = length != C.LENGTH_UNSET.toLong()
        val total = if (watching && dataSpec.length == C.LENGTH_UNSET.toLong()) dataSpec.position + length else 0
        minBytesPerSecond = requiredBytesPerSecond(bitrateKbps, durationSeconds, total)
        rangeUnchecked = watching && dataSpec.position > 0
        checkRange()
        return length
    }

    /**
     * With the streaming cache on, the network connection may only open later, inside [read], so
     * the response is checked once its headers are there.
     */
    private fun checkRange() {
        if (!rangeUnchecked) return
        val headers = upstream.responseHeaders
        if (headers.isEmpty()) return
        rangeUnchecked = false
        if (headers.keys.none { "Content-Range".equals(it, ignoreCase = true) }) {
            rangesIgnored = true
            Log.w(TAG, "The server ignores range requests, so this stream is not reconnected again")
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val start = clock()
        val read = upstream.read(buffer, offset, length)
        if (!watching) return read
        checkRange()
        if (read == C.RESULT_END_OF_INPUT) {
            // The whole stream arrived, so whatever the last reconnect was for, it worked.
            verdictPending = false
            return read
        }

        val waitedMs = clock() - start
        readMs += waitedMs
        totalReadMs += waitedMs
        bytes += read
        // Reads served from memory or the cache take no measurable time; merging them keeps the
        // window to one sample per millisecond of reading.
        if (samples.last().readMs == readMs) samples.removeLast()
        samples.addLast(Sample(readMs, bytes))
        // Slide the window: keep the newest sample that is still at least windowMs back.
        while (samples.size > 1 && readMs - samples[1].readMs >= windowMs) samples.removeFirst()

        val oldest = samples.first()
        val spanMs = readMs - oldest.readMs
        if (spanMs < windowMs) return read

        val bytesPerSecond = (bytes - oldest.bytes) * 1000 / spanMs
        if (bytesPerSecond >= minBytesPerSecond) {
            verdictPending = false
            if (tooSlowForStream) {
                tooSlowForStream = false
                Log.i(TAG, "Stream back to ${kbps(bytesPerSecond)} kbps, watching it again")
            }
        } else if (verdictPending) {
            verdictPending = false
            tooSlowForStream = true
            tooSlowSinceMs = totalReadMs
            Log.w(TAG, "Still ${kbps(bytesPerSecond)} kbps after reconnecting (needs ${kbps(minBytesPerSecond)} " +
                    "kbps): the network is too slow for this stream")
        } else if (mayReconnect()) {
            verdictPending = true
            stalledOpenPosition = openPosition
            stallPosition = openPosition + bytes
            restartChecked = false
            Log.w(TAG, "Stream at ${kbps(bytesPerSecond)} kbps for ${windowMs / 1000} s (needs " +
                    "${kbps(minBytesPerSecond)} kbps), reconnecting: ${upstream.uri?.path}")
            throw StalledStreamException(bytesPerSecond, minBytesPerSecond)
        }
        return read
    }

    private fun mayReconnect(): Boolean {
        if (rangesIgnored || restarts >= MAX_RESTARTS || openPosition + bytes < noReconnectBefore) return false
        if (!tooSlowForStream) return true
        return restarts == 0 && totalReadMs - tooSlowSinceMs >= RETRY_WHEN_TOO_SLOW_MS
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        // A connection that ran fast enough for a while before it ended showed the reconnect
        // worked, even without a whole window. One that failed or barely started showed nothing.
        if (verdictPending && openedAfterReconnect && readMs >= EVIDENCE_MS && bytes * 1000 / readMs >= minBytesPerSecond) {
            verdictPending = false
        }
        upstream.close()
    }

    class StalledStreamException(val bytesPerSecond: Long, val minBytesPerSecond: Long) :
        IOException("Stream stalled at $bytesPerSecond B/s, below $minBytesPerSecond B/s")

    /**
     * The media source's load error policy, for one song. Media3 gives up on a load after a few
     * errors in a row that brought no audio, and a reconnect while the header is still loading
     * brings none, so without this each one would use up a retry that a real network error may
     * need. Reconnects are retried at once and allowed on top of the default limit, up to
     * [MAX_EXTRA_RETRIES] so that a network that is really gone still ends in an error. Every other
     * error is handled as before, though Media3 waits a little longer before retrying it when our
     * reconnects came first in the same run of errors.
     */
    class RetryPolicy(
        private val delegate: LoadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(),
    ) : LoadErrorHandlingPolicy by delegate {
        private var reconnects = 0

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            if (loadErrorInfo.exception !is StalledStreamException) return delegate.getRetryDelayMsFor(loadErrorInfo)
            reconnects++
            return 0
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int =
            delegate.getMinimumLoadableRetryCount(dataType) + minOf(reconnects, MAX_EXTRA_RETRIES)
    }

    class Factory(
        private val upstream: DataSource.Factory,
        private val bitrateKbps: Int,
        private val durationSeconds: Int,
    ) : DataSource.Factory {
        constructor(upstream: DataSource.Factory, mediaItem: MediaItem) : this(
            upstream,
            mediaItem.mediaMetadata.extras?.getInt("bitrate", 0) ?: 0,
            mediaItem.mediaMetadata.extras?.getInt("duration", 0) ?: 0,
        )

        override fun createDataSource(): DataSource =
            StallDetectingDataSource(upstream.createDataSource(), bitrateKbps, durationSeconds)
    }

    companion object {
        private const val TAG = "StallDetectingDataSource"
        const val WINDOW_MS = 8_000L

        /** How long a connection after a reconnect must read fast enough to count, if it ends early. */
        const val EVIDENCE_MS = 1_000L

        /** While the network is too slow, how much slow reading before trying a reconnect anyway. */
        const val RETRY_WHEN_TOO_SLOW_MS = 60_000L

        /**
         * A margin above what Media3 normally holds read but not yet used while playing audio, so a
         * resume rarely goes back this far. Not a hard limit: a large peek can hold more.
         */
        const val UNUSED_BYTES = 512 * 1024L

        /** Headers sit at the start of a file; a stall further in is past them, with room for a large picture. */
        const val HEADER_BYTES = 32 * 1024 * 1024L

        /** Reconnects that start a header over, per song. */
        const val MAX_RESTARTS = 2

        /** How many of our reconnects the retry policy adds to Media3's limit. */
        const val MAX_EXTRA_RETRIES = 3

        /** Used when neither the song's bitrate nor the stream's size is known. */
        const val FALLBACK_KBPS = 128

        private fun kbps(bytesPerSecond: Long) = bytesPerSecond * 8 / 1000

        /**
         * The rate a stream must sustain to play in real time. The song's bitrate describes the
         * original file, but the server may send a transcode instead (for example when the app
         * leaves the format to the server), so the stream's own size over the song's duration is
         * used too, and the lower of the two wins. That also keeps a large embedded picture, which
         * inflates the size, from raising the bar, unless the song's bitrate is unknown.
         */
        fun requiredBytesPerSecond(bitrateKbps: Int, durationSeconds: Int, totalBytes: Long): Long {
            val fromBitrate = if (bitrateKbps > 0) bitrateKbps * 1000L / 8 else Long.MAX_VALUE
            val fromSize = if (durationSeconds > 0 && totalBytes > 0) totalBytes / durationSeconds else Long.MAX_VALUE
            val required = minOf(fromBitrate, fromSize)
            return if (required == Long.MAX_VALUE) FALLBACK_KBPS * 1000L / 8 else required
        }
    }
}
