package com.eddyizm.tempus.util

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.IOException

class StallDetectingDataSourceTest {

    private var now = 0L

    private fun spec(position: Long = 0) = DataSpec.Builder().setUri(mock<Uri>()).setPosition(position).build()

    /** Each read returns [bytesPerRead] bytes after [msPerRead] of (fake) waiting. */
    private inner class FakeUpstream(
        var bytesPerRead: Int,
        var msPerRead: Long,
        private val length: Long = 100_000_000,
    ) : DataSource {
        /** Where in the stream the next byte read comes from. */
        var position = 0L
        var failNextOpen = false
        var failNextRead = false
        var endOfInput = false
        /** The response headers; a server that honours range requests sends Content-Range. */
        var headers: Map<String, List<String>> = mapOf("Content-Range" to listOf("bytes 0-1/2"))
        /** With the streaming cache on, the network connection (and its headers) may come later. */
        var headersAfterReads = 0
        private var readsSinceOpen = 0

        override fun open(dataSpec: DataSpec): Long {
            if (failNextOpen) { failNextOpen = false; throw IOException("connect timed out") }
            position = dataSpec.position
            readsSinceOpen = 0
            return if (length == C.LENGTH_UNSET.toLong()) length else length - dataSpec.position
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (failNextRead) { failNextRead = false; throw IOException("read timed out") }
            if (endOfInput) return C.RESULT_END_OF_INPUT
            check(bytesPerRead <= length) { "a read may not return more than was asked for" }
            readsSinceOpen++
            now += msPerRead
            position += bytesPerRead
            return bytesPerRead
        }
        override fun addTransferListener(transferListener: TransferListener) {}
        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> =
            if (readsSinceOpen >= headersAfterReads) headers else emptyMap()
        override fun close() {}

        // At 50 kbps: 900-byte segments every ~144 ms, as on the stuck connection.
        fun stuck() = apply { bytesPerRead = 900; msPerRead = 144 }
        // About 6.5 Mbps.
        fun fast() = apply { bytesPerRead = 16_384; msPerRead = 20 }
    }

    private fun stuck() = FakeUpstream(0, 0).stuck()
    private fun fast() = FakeUpstream(0, 0).fast()

    /** A 192 kbps song: the bitrate of the stalled tracks. */
    private fun source(upstream: DataSource, bitrateKbps: Int = 192, durationSeconds: Int = 0) =
        StallDetectingDataSource(upstream, bitrateKbps, durationSeconds, windowMs = 8_000) { now }

    private val buffer = ByteArray(65_536)

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

    /**
     * What Media3 does mid-song after a load error: close the stalled connection and open a new
     * one where the extractor got to, a little before the last byte read.
     */
    private fun resume(source: DataSource, upstream: FakeUpstream) {
        source.close()
        source.open(spec(maxOf(0, upstream.position - 16_384)))
    }

    /** What Media3 does when the error came while a header was being read: start it over. */
    private fun restart(source: DataSource, at: Long = 0) {
        source.close()
        source.open(spec(at))
    }

    @Test
    fun healthyStream_neverThrows() {
        val source = source(fast()).apply { open(spec()) }
        readFor(source, 300_000)
    }

    @Test
    fun stuckStream_throwsAfterOneWindow() {
        val source = source(stuck()).apply { open(spec()) }
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun collapseAfterAFastStart_isCaughtOneWindowAfterTheCollapse() {
        // The fast bytes from before the collapse must not hide it for an extra window.
        val upstream = fast()
        val source = source(upstream).apply { open(spec()) }
        readFor(source, 5_000)
        upstream.stuck()
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun timeIdleBetweenReads_doesNotCount() {
        // A full buffer pauses the loader between reads; that is not a slow network.
        val source = source(fast()).apply { open(spec()) }
        repeat(1_000) {
            source.read(buffer, 0, buffer.size)
            now += 5_000
        }
    }

    @Test
    fun readsThatTakeNoTime_stillLetAStallBeCaught() {
        // Reads served from the cache take 0 ms; they are merged and must not upset the window.
        val upstream = FakeUpstream(bytesPerRead = 1_024, msPerRead = 0)
        val source = source(upstream).apply { open(spec()) }
        repeat(100_000) { source.read(buffer, 0, buffer.size) }
        upstream.stuck()
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun stuckConnection_whenTheNewOneIsFast_keepsWatching() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec()) }
        readUntilStall(source)

        resume(source, upstream)
        upstream.fast()
        readFor(source, 10_000)

        // The connection was at fault, so a later stall reconnects again.
        upstream.stuck()
        readUntilStall(source)
    }

    @Test
    fun aLongSong_recoversFromEveryStuckConnection() {
        // A two-hour mix with many separate episodes: there is no limit per song.
        val upstream = fast()
        val source = source(upstream).apply { open(spec()) }
        repeat(6) {
            readFor(source, 8_000)
            upstream.stuck()
            readUntilStall(source)
            resume(source, upstream)
            upstream.fast()
        }
    }

    @Test
    fun aSlowNetworkMidSong_reconnectsOnce_thenOnlyOnceAMinute() {
        val upstream = FakeUpstream(bytesPerRead = 1_875, msPerRead = 100) // 150 kbps for a 192 kbps song
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)

        resume(source, upstream)
        // The new connection is just as slow (8 s), then a minute passes before the next try.
        val took = readUntilStall(source)
        assertEquals(68_000.0, took.toDouble(), 200.0)
    }

    @Test
    fun aStuckConnectionAfterAReconnectDuringADip_isRetriedAfterAMinute() {
        // The reconnect happened during a brief dip, so its first window was slow and the network
        // looked too slow; then that connection got stuck too, while the network recovered.
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)
        resume(source, upstream)
        readUntilStall(source, limitMs = 70_000)

        resume(source, upstream)
        upstream.fast()
        readFor(source, 8_000)
        upstream.stuck()
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0) // watching normally again
    }

    @Test
    fun aSlowNetworkDuringAHeader_restartsItOnce_andNeverAgain() {
        // A 2.5 Mbps FLAC with a 13 MB cover over a 700 kbps link, streaming cache off. Every
        // connection is slow, and a reconnect starts the picture over.
        val upstream = FakeUpstream(bytesPerRead = 8_750, msPerRead = 100) // 700 kbps
        val source = source(upstream, bitrateKbps = 2_500).apply { open(spec()) }
        readUntilStall(source)

        restart(source)
        readFor(source, 600_000) // no minutely tries after a restart
        restart(source) // e.g. the player seeks back to the start
        readFor(source, 600_000)
    }

    @Test
    fun aFlakyNetworkDuringAHeader_restartsItAtMostTwice() {
        // Fast for 20 s, slow for 20 s, again and again, while a huge header loads: each restart
        // would start the picture over and playback would never begin.
        val upstream = FakeUpstream(0, 0)
        val source = source(upstream, bitrateKbps = 2_500).apply { open(spec()) }
        var restarts = 0
        val end = now + 30 * 60_000
        while (now < end) {
            if ((now / 20_000) % 2 == 0L) { upstream.bytesPerRead = 37_500; upstream.msPerRead = 100 } // 3 Mbps
            else { upstream.bytesPerRead = 6_250; upstream.msPerRead = 100 } // 500 kbps
            try {
                source.read(buffer, 0, buffer.size)
            } catch (e: StallDetectingDataSource.StalledStreamException) {
                restarts++
                restart(source)
            }
        }
        assertTrue("restarted $restarts times", restarts in 1..2)
    }

    @Test
    fun afterARestart_noReconnectUntilTheStreamIsBackWhereItStalled() {
        val upstream = fast()
        val source = source(upstream).apply { open(spec()) }
        readFor(source, 3_000) // ~2.5 MB of header
        upstream.stuck()
        readUntilStall(source)
        val stalledAt = upstream.position

        restart(source)
        upstream.fast()
        readFor(source, 1_000)
        upstream.stuck()
        // Slow again, but still re-reading what the stalled connection had already brought.
        while (upstream.position + upstream.bytesPerRead < stalledAt) source.read(buffer, 0, buffer.size)
        // Once past that point, a stall reconnects again.
        readUntilStall(source, limitMs = 10_000)
    }

    @Test
    fun aRestartToTheStartOfAPictureFurtherIn_isRecognised() {
        // The picture starts 50 KB in, after other header data the player already used, so the
        // restart goes back to 50 KB rather than to where the stalled connection began.
        val upstream = fast()
        val source = source(upstream, bitrateKbps = 2_500).apply { open(spec()) }
        readFor(source, 3_000) // ~2.5 MB into the picture
        upstream.bytesPerRead = 8_750; upstream.msPerRead = 100 // then 700 kbps
        readUntilStall(source)
        restart(source, at = 50_000)
        readFor(source, 600_000) // a restart: no minutely tries afterwards
    }

    @Test
    fun aSeekBackJustAsTheWatchdogFires_deepIntoAFile_isNotTakenForARestart() {
        // A long podcast stalls around 80 MB; the user seeks back to 1 MB at that moment, so the
        // next connection is the seek's, not the retry's.
        val upstream = FakeUpstream(bytesPerRead = 625, msPerRead = 100) // 50 kbps
        val source = source(upstream).apply { open(spec(80_000_000)) }
        readUntilStall(source)

        restart(source, at = 1_000_000)
        upstream.fast()
        readFor(source, 8_000)
        upstream.bytesPerRead = 625; upstream.msPerRead = 100
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 100.0)
    }

    @Test
    fun aSeekBackToBeforeWhereTheStalledConnectionStarted_isNotTakenForARestart() {
        // Early in a song: the stalled connection had resumed at 5 MB, the seek goes to 1 MB. A
        // header restart never goes back before where the stalled connection started.
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)

        restart(source, at = 1_000_000)
        upstream.fast()
        readFor(source, 1_000) // still well before 5 MB
        upstream.stuck()
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 1_100.0)
    }

    @Test
    fun aSeekBackAfterAStallPastTheHeaderArea_isNotTakenForARestart() {
        // The first connection played 40 MB before it got stuck: that is no header.
        val upstream = fast()
        val source = source(upstream).apply { open(spec()) }
        while (upstream.position < 40_000_000) source.read(buffer, 0, buffer.size)
        upstream.stuck()
        readUntilStall(source)

        restart(source, at = 1_000_000)
        upstream.fast()
        readFor(source, 8_000)
        upstream.stuck()
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun aServerThatIgnoresRangeRequests_isNotReconnectedAgain() {
        // Each reconnect would download and drop everything before the position.
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(40_000_000)) }
        readUntilStall(source)

        upstream.headers = mapOf("Content-Length" to listOf("100000000"))
        resume(source, upstream)
        upstream.fast()
        readFor(source, 8_000)
        upstream.stuck()
        readFor(source, 600_000)
    }

    @Test
    fun aServerThatIgnoresRangeRequests_isFoundOutWhenTheCacheOpensTheNetworkLater() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(40_000_000)) }
        readUntilStall(source)

        upstream.headers = mapOf("content-length" to listOf("100000000"))
        upstream.headersAfterReads = 3 // read from the cache first
        resume(source, upstream)
        upstream.fast()
        readFor(source, 8_000)
        upstream.stuck()
        readFor(source, 600_000)
    }

    @Test
    fun aServerThatHonoursRangeRequests_isReconnectedAgain() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(40_000_000)) }
        readUntilStall(source)

        upstream.headers = mapOf("content-range" to listOf("bytes 39983616-99999999/100000000")) // OkHttp lowercases
        resume(source, upstream)
        upstream.fast()
        readFor(source, 8_000)
        upstream.stuck()
        readUntilStall(source)
    }

    @Test
    fun aRequestFromTheStart_isNotTakenForAnIgnoredRange() {
        // A plain 200 is the right answer to a request from byte 0.
        val upstream = stuck().apply { headers = mapOf("Content-Length" to listOf("100000000")) }
        val source = source(upstream).apply { open(spec()) }
        readUntilStall(source)
        resume(source, upstream.apply { headers = mapOf("Content-Range" to listOf("bytes 1-2/3")) })
        upstream.fast()
        readFor(source, 8_000)
        upstream.stuck()
        readUntilStall(source)
    }

    @Test
    fun aStalledConnectionThatWasFastBeforeTheCollapse_doesNotCountAsTheReconnectWorking() {
        // Closing the stalled connection must not settle the verdict: its average looks healthy.
        val upstream = fast()
        val source = source(upstream).apply { open(spec()) }
        readFor(source, 5_000)
        upstream.stuck()
        readUntilStall(source)

        resume(source, upstream)
        readFor(source, 50_000) // the network is slow now: one reconnect, then a minute's rest
    }

    @Test
    fun aReconnectThatFailsToConnect_stillHasTheNextConnectionJudged() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)

        source.close()
        upstream.failNextOpen = true
        assertThrows(IOException::class.java) { source.open(spec(upstream.position)) }
        source.close() // Media3 closes a source whose open failed, too
        source.open(spec(upstream.position)) // and retries
        readFor(source, 50_000)
    }

    @Test
    fun aConnectionAfterTheReconnectThatDiesEarly_leavesTheNextOneJudged() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)

        resume(source, upstream)
        readFor(source, 3_000)
        upstream.failNextRead = true
        assertThrows(IOException::class.java) { source.read(buffer, 0, buffer.size) }
        resume(source, upstream)
        readFor(source, 50_000)
    }

    @Test
    fun slowNetwork_isWatchedAgain_onceAWholeWindowRunsFastEnough() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)
        resume(source, upstream)
        readFor(source, 30_000) // still stuck: the network, not the connection

        upstream.fast()
        readFor(source, 8_000)
        upstream.stuck()
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun aConnectionAfterTheReconnectThatRanFastThenEnded_showsTheReconnectWorked() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)

        // Fast for 2 s, then the player seeks.
        resume(source, upstream)
        upstream.fast()
        readFor(source, 2_000)
        source.close()
        source.open(spec(20_000_000))

        upstream.stuck()
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun theEndOfTheStream_showsTheReconnectWorked() {
        val upstream = stuck()
        val source = source(upstream).apply { open(spec(5_000_000)) }
        readUntilStall(source)

        resume(source, upstream)
        upstream.endOfInput = true
        assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, buffer.size))
        upstream.endOfInput = false

        source.close()
        source.open(spec(20_000_000)) // e.g. the player seeks
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun aServerTranscode_isJudgedByTheStreamSent_notTheOriginalFile() {
        // Prioritize server transcode settings: the URL has no format, the item says 889 kbps
        // FLAC, and Navidrome sends a cached 192 kbps MP3 with a known length over a 500 kbps link.
        val upstream = FakeUpstream(bytesPerRead = 6_250, msPerRead = 100, length = 200 * 192_000L / 8)
        val source = source(upstream, bitrateKbps = 889, durationSeconds = 200).apply { open(spec()) }
        readFor(source, 600_000)
    }

    @Test
    fun requiredRate_isTheLowerOfTheSongBitrateAndTheStreamSize() {
        // A cached 192 kbps transcode of an 889 kbps FLAC: the stream's size wins.
        assertEquals(24_000L, StallDetectingDataSource.requiredBytesPerSecond(889, 200, 4_800_000))
        // A 962 kbps FLAC with a 13 MB cover: the picture must not raise the bar above the audio.
        assertEquals(120_250L, StallDetectingDataSource.requiredBytesPerSecond(962, 240, 28_860_000 + 13_000_000))
        // Only one of them known.
        assertEquals(24_000L, StallDetectingDataSource.requiredBytesPerSecond(0, 200, 4_800_000))
        assertEquals(24_000L, StallDetectingDataSource.requiredBytesPerSecond(192, 0, 0))
        // Neither known: 128 kbps.
        assertEquals(16_000L, StallDetectingDataSource.requiredBytesPerSecond(0, 0, 0))
    }

    @Test
    fun aRangeRequest_isJudgedByTheWholeStream_notJustTheRest() {
        // On a resume halfway through, the server sends only the second half. The stream needs
        // 4.8 MB / 200 s = 192 kbps; counting only the half sent would make it look like 96 kbps,
        // and a 150 kbps link would wrongly pass.
        val upstream = FakeUpstream(bytesPerRead = 1_875, msPerRead = 100, length = 4_800_000) // 150 kbps
        val source = source(upstream, bitrateKbps = 889, durationSeconds = 200)
        source.open(spec(2_400_000))
        readUntilStall(source)
    }

    @Test
    fun aBoundedRequest_isJudgedByTheSongBitrateOnly() {
        // With a length in the request the stream's total size is unknown. Taking the 2.4 MB asked
        // for as the whole stream would make it look like 96 kbps, and a 110 kbps link would pass.
        val upstream = FakeUpstream(bytesPerRead = 1_375, msPerRead = 100, length = 2_400_000) // 110 kbps
        val source = source(upstream, bitrateKbps = 128, durationSeconds = 200)
        source.open(DataSpec.Builder().setUri(mock<Uri>()).setLength(2_400_000).build())
        readUntilStall(source)
    }

    @Test
    fun reopening_startsAFreshMeasurement() {
        val source = source(stuck())
        source.open(spec())
        readFor(source, 6_000)
        source.open(spec())
        assertEquals(8_000.0, readUntilStall(source).toDouble(), 144.0)
    }

    @Test
    fun unknownLength_isNotWatched() {
        // The player cannot resume such a stream mid-file, so reconnecting would restart it.
        val source = source(FakeUpstream(900, 144, length = C.LENGTH_UNSET.toLong()))
        source.open(spec())
        readFor(source, 300_000)
    }

    private fun errorInfo(exception: IOException, errorCount: Int) = LoadErrorInfo(
        LoadEventInfo(0, spec(), mock<Uri>(), emptyMap(), 0, 0, 0),
        MediaLoadData(C.DATA_TYPE_MEDIA),
        exception,
        errorCount,
    )

    private val stall = StallDetectingDataSource.StalledStreamException(6_000, 24_000)

    @Test
    fun retryPolicy_retriesAReconnectAtOnce_withoutUsingUpTheRetryLimit() {
        val policy = StallDetectingDataSource.RetryPolicy()
        val limit = policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA)

        assertEquals(0L, policy.getRetryDelayMsFor(errorInfo(stall, errorCount = 3)))
        assertEquals(limit + 1, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
        assertEquals(0L, policy.getRetryDelayMsFor(errorInfo(stall, errorCount = 4)))
        assertEquals(limit + 2, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    @Test
    fun retryPolicy_addsAtMostThreeRetries() {
        // After many fixed stalls, a network that is really gone must still end in an error.
        val policy = StallDetectingDataSource.RetryPolicy()
        val limit = policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA)
        repeat(10) { policy.getRetryDelayMsFor(errorInfo(stall, errorCount = 2)) }
        assertEquals(limit + 3, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }

    @Test
    fun retryPolicy_handlesOtherErrorsAsBefore() {
        val policy = StallDetectingDataSource.RetryPolicy()
        val limit = policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA)
        assertEquals(1_000L, policy.getRetryDelayMsFor(errorInfo(IOException("read timed out"), errorCount = 2)))
        assertEquals(limit, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }
}
