package com.eddyizm.tempus.util

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock

class StallDetectingDataSourceTest {

    private var now = 0L
    private val dataSpec = mock<DataSpec>()

    /** Each read returns [bytesPerRead] bytes after [msPerRead] of (fake) waiting. */
    private inner class FakeUpstream(
        var bytesPerRead: Int,
        var msPerRead: Long,
        private val length: Long = 10_000_000,
    ) : DataSource {
        var opens = 0
        override fun open(dataSpec: DataSpec): Long { opens++; return length }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            now += msPerRead
            return bytesPerRead
        }
        override fun addTransferListener(transferListener: TransferListener) {}
        override fun getUri(): Uri? = null
        override fun close() {}
    }

    // 192 kbps, the bitrate of the stalled tracks; the stuck connection delivered ~50 kbps.
    private val needed = 192_000L / 8

    // At 50 kbps: 900-byte segments every ~140 ms.
    private fun stuck() = FakeUpstream(bytesPerRead = 900, msPerRead = 144)

    private fun healthy() = FakeUpstream(bytesPerRead = 16_384, msPerRead = 20)

    private fun source(upstream: DataSource) =
        StallDetectingDataSource(upstream, needed, baseWindowMs = 8_000, maxWindowMs = 32_000) { now }

    private val buffer = ByteArray(16_384)

    /** Reads until the source throws and returns how much read time that took. */
    private fun readUntilStall(source: DataSource, limitMs: Long = 600_000): Long {
        val start = now
        assertThrows(StallDetectingDataSource.StalledStreamException::class.java) {
            while (now - start < limitMs) source.read(buffer, 0, buffer.size)
        }
        return now - start
    }

    private fun readFor(source: DataSource, ms: Long) {
        val end = now + ms
        while (now < end) source.read(buffer, 0, buffer.size)
    }

    @Test
    fun healthyStream_neverThrows() {
        val source = source(healthy()).apply { open(dataSpec) }
        readFor(source, 300_000)
    }

    @Test
    fun stuckStream_throwsAfterOneWindow() {
        val source = source(stuck()).apply { open(dataSpec) }
        val took = readUntilStall(source)
        assertEquals(8_000.0, took.toDouble(), 144.0)
    }

    @Test
    fun collapseAfterAFastStart_isCaughtOneWindowAfterTheCollapse() {
        // The fast bytes from before the collapse must not hide it for an extra window.
        val upstream = healthy()
        val source = source(upstream).apply { open(dataSpec) }
        readFor(source, 5_000)
        upstream.bytesPerRead = 900; upstream.msPerRead = 144
        val took = readUntilStall(source)
        assertEquals(8_000.0, took.toDouble(), 144.0)
    }

    @Test
    fun timeIdleBetweenReads_doesNotCount() {
        // A full buffer pauses the loader between reads; that is not a slow network.
        val source = source(healthy()).apply { open(dataSpec) }
        repeat(1_000) {
            source.read(buffer, 0, buffer.size)
            now += 5_000
        }
    }

    @Test
    fun consecutiveStalls_doubleTheWindow_upToTheMaximum() {
        val upstream = stuck()
        val source = source(upstream)
        val windows = (1..4).map {
            source.open(dataSpec)
            readUntilStall(source) / 1_000
        }
        assertEquals(listOf(8L, 16L, 32L, 32L), windows)
    }

    @Test
    fun aHealthyWindow_resetsTheBackoff() {
        val upstream = stuck()
        val source = source(upstream)
        source.open(dataSpec)
        readUntilStall(source)

        source.open(dataSpec)
        upstream.bytesPerRead = 16_384; upstream.msPerRead = 20
        readFor(source, 16_000) // a full (doubled) window at full speed

        upstream.bytesPerRead = 900; upstream.msPerRead = 144
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun reopening_startsAFreshMeasurement() {
        val upstream = stuck()
        val source = source(upstream)
        source.open(dataSpec)
        readFor(source, 6_000)
        source.open(dataSpec)
        assertEquals(8L, readUntilStall(source) / 1_000)
    }

    @Test
    fun unknownLength_isNotWatched() {
        // The player cannot resume such a stream mid-file, so reconnecting would restart it.
        val source = source(FakeUpstream(900, 144, length = C.LENGTH_UNSET.toLong()))
        source.open(dataSpec)
        readFor(source, 300_000)
    }

    @Test
    fun endOfInput_passesThroughWithoutCountingTime() {
        val upstream = object : DataSource by stuck() {
            override fun open(dataSpec: DataSpec) = 1L
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                now += 60_000
                return C.RESULT_END_OF_INPUT
            }
        }
        val source = source(upstream)
        source.open(dataSpec)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, buffer.size))
    }
}
