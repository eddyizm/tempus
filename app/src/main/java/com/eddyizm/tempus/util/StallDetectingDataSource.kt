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
import java.io.IOException

/**
 * Fails a stream whose connection has slowed to a trickle, so the player reconnects.
 *
 * A TCP connection can get stuck at roughly one segment per round trip (~50 kbps on mobile data)
 * and stay there for minutes while new connections to the same server run at full speed. Data
 * keeps arriving, so no read timeout fires, and playback alternates between a few seconds of audio
 * and long buffering pauses. Skipping the track fixes it, because that opens a new connection.
 *
 * This wrapper does the same thing automatically: it measures throughput over the time spent
 * waiting inside [read] (time the loader spends idle with a full buffer does not count), over a
 * window sliding along that read time, and once the rate over a whole window is below
 * [minBytesPerSecond] it throws [StalledStreamException].
 * `ProgressiveMediaPeriod` treats that as a retryable load error and reopens the stream at the
 * current position with a range request, with no delay for the first retry. Each consecutive stall
 * doubles the window, so a network that is simply too slow for the track gets a reconnect every
 * couple of minutes at most rather than every few seconds.
 */
@UnstableApi
class StallDetectingDataSource(
    private val upstream: DataSource,
    private val minBytesPerSecond: Long,
    private val baseWindowMs: Long = DEFAULT_WINDOW_MS,
    private val maxWindowMs: Long = MAX_WINDOW_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : DataSource {

    /** Running totals of read time and bytes after a read; the window is the span between two. */
    private class Sample(val readMs: Long, val bytes: Long)

    private var windowMs = baseWindowMs
    private var watching = false
    private var readMs = 0L
    private var bytes = 0L
    private val samples = ArrayDeque<Sample>()

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        // Each connection starts a fresh measurement; the backoff in windowMs carries over.
        readMs = 0
        bytes = 0
        samples.clear()
        samples.addLast(Sample(0, 0))
        val length = upstream.open(dataSpec)
        // Without a known length the player cannot resume mid-file, so a reconnect would restart it.
        watching = length != C.LENGTH_UNSET.toLong()
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val start = clock()
        val read = upstream.read(buffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT || !watching) return read

        readMs += clock() - start
        bytes += read
        samples.addLast(Sample(readMs, bytes))
        // Slide the window: keep the newest sample that is still at least windowMs back.
        while (samples.size > 1 && readMs - samples[1].readMs >= windowMs) samples.removeFirst()

        val oldest = samples.first()
        val spanMs = readMs - oldest.readMs
        if (spanMs >= windowMs) {
            val bytesPerSecond = (bytes - oldest.bytes) * 1000 / spanMs
            if (bytesPerSecond < minBytesPerSecond) {
                val stalledFor = windowMs
                windowMs = minOf(windowMs * 2, maxWindowMs)
                Log.w(TAG, "Stream at ${bytesPerSecond * 8 / 1000} kbps for ${stalledFor / 1000} s " +
                        "(needs ${minBytesPerSecond * 8 / 1000} kbps), reconnecting: ${upstream.uri?.path}")
                throw StalledStreamException(bytesPerSecond, minBytesPerSecond)
            }
            windowMs = baseWindowMs
        }
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    class StalledStreamException(val bytesPerSecond: Long, val minBytesPerSecond: Long) :
        IOException("Stream stalled at $bytesPerSecond B/s, below $minBytesPerSecond B/s")

    class Factory(
        private val upstream: DataSource.Factory,
        private val minBytesPerSecond: Long,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            StallDetectingDataSource(upstream.createDataSource(), minBytesPerSecond)
    }

    companion object {
        private const val TAG = "StallDetectingDataSource"
        const val DEFAULT_WINDOW_MS = 8_000L
        const val MAX_WINDOW_MS = 120_000L

        /** Used when the item does not say what bitrate to expect. */
        const val FALLBACK_KBPS = 128

        /** The rate a stream must sustain to play in real time: the track's bitrate, or [FALLBACK_KBPS]. */
        fun minBytesPerSecondFor(mediaItem: MediaItem): Long {
            val bitrate = mediaItem.mediaMetadata.extras?.getInt("bitrate", 0) ?: 0
            val kbps = if (bitrate > 0) bitrate else FALLBACK_KBPS
            return kbps * 1000L / 8
        }
    }
}
