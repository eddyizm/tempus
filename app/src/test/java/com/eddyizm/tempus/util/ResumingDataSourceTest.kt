package com.eddyizm.tempus.util

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException

class ResumingDataSourceTest {

    private val content = ByteArray(10_000) { (it % 251).toByte() }
    private val spec = DataSpec.Builder().setUri(mock<Uri>()).build()
    private val sleeps = mutableListOf<Long>()

    /** Serves [content] from the requested position, like a server with the transcode cached. */
    private inner class FakeServer : DataSource {
        var position = 0
        val opens = mutableListOf<Long>()
        var breakAt = -1
        var failOpens = 0
        var honoursRange = true
        var breakWith: IOException = SocketException("Connection reset")
        private var openedAt = 0L

        override fun open(dataSpec: DataSpec): Long {
            opens += dataSpec.position
            if (opens.size > 1 && failOpens > 0) { failOpens--; throw IOException("Unable to resolve host") }
            openedAt = if (honoursRange) dataSpec.position else 0
            position = openedAt.toInt()
            return C.LENGTH_UNSET.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == breakAt) {
                breakAt = -1
                throw HttpDataSourceException(breakWith, spec,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, HttpDataSourceException.TYPE_READ)
            }
            if (position >= content.size) return C.RESULT_END_OF_INPUT
            val end = if (breakAt in (position + 1)..content.size) breakAt else content.size
            val n = minOf(length, 700, end - position)
            System.arraycopy(content, position, buffer, offset, n)
            position += n
            return n
        }

        override fun getResponseHeaders(): Map<String, List<String>> =
            if (honoursRange && openedAt > 0) mapOf("content-range" to listOf("bytes $openedAt-*/*")) else emptyMap()

        override fun addTransferListener(transferListener: TransferListener) {}
        override fun getUri(): Uri? = null
        override fun close() {}
    }

    private fun readAll(source: DataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        source.open(spec)
        while (true) {
            val n = source.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        source.close()
        return out.toByteArray()
    }

    private fun resuming(server: FakeServer) = ResumingDataSource(server, sleep = { sleeps += it })

    @Test
    fun aBrokenStreamCarriesOnFromTheByteItReached() {
        val server = FakeServer().apply { breakAt = 4_200 }

        val read = readAll(resuming(server))

        assertArrayEquals(content, read)
        assertEquals(listOf(0L, 4_200L), server.opens)
    }

    @Test
    fun aServerThatIgnoresTheRangeIsNotSplicedIn() {
        val server = FakeServer().apply { breakAt = 4_200; honoursRange = false }
        val source = resuming(server)

        val error = assertThrows(HttpDataSourceException::class.java) { readAll(source) }

        assertSame(server.breakWith, error.cause)
        assertEquals(listOf(0L, 4_200L), server.opens)
    }

    @Test
    fun aFailedReconnectIsRetriedWithBackoff() {
        val server = FakeServer().apply { breakAt = 3_000; failOpens = 2 }

        val read = readAll(resuming(server))

        assertArrayEquals(content, read)
        assertEquals(listOf(0L, 3_000L, 3_000L, 3_000L), server.opens)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), sleeps)
    }

    @Test
    fun itGivesUpWhenTheNetworkStaysDown() {
        val server = FakeServer().apply { breakAt = 3_000; failOpens = 100 }
        val source = resuming(server)

        assertThrows(HttpDataSourceException::class.java) { readAll(source) }

        assertEquals(1 + ResumingDataSource.MAX_ATTEMPTS, server.opens.size)
    }

    @Test
    fun aBreakBeforeAnyDataIsLeftToTheCaller() {
        val server = FakeServer().apply { breakAt = 0 }
        val source = resuming(server)

        assertThrows(HttpDataSourceException::class.java) { readAll(source) }

        assertEquals(listOf(0L), server.opens)
    }

    @Test
    fun aReadTimeoutIsResumed() {
        val server = FakeServer().apply { breakAt = 3_000; breakWith = SocketTimeoutException("timeout") }

        val read = readAll(resuming(server))

        assertArrayEquals(content, read)
        assertEquals(listOf(0L, 3_000L), server.opens)
    }

    @Test
    fun aCancelledLoadIsNotResumed() {
        val server = FakeServer().apply { breakAt = 3_000; breakWith = SocketTimeoutException("timeout") }
        val source = resuming(server)

        try {
            Thread.currentThread().interrupt()
            assertThrows(HttpDataSourceException::class.java) { readAll(source) }
        } finally {
            Thread.interrupted()
        }

        assertEquals(listOf(0L), server.opens)
    }

    @Test
    fun anInterruptedReadIsNotResumed() {
        val server = FakeServer().apply { breakAt = 3_000; breakWith = InterruptedIOException() }
        val source = resuming(server)

        assertThrows(HttpDataSourceException::class.java) { readAll(source) }

        assertEquals(listOf(0L), server.opens)
    }
}
