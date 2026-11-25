package com.eddyizm.tempus.util

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Carries on a stream whose connection dropped part-way, from the byte it had reached, so the
 * player above never sees the break.
 *
 * Use it below the streaming cache, so the cache sees one uninterrupted stream.
 */
@UnstableApi
class ResumingDataSource(
    private val upstream: DataSource,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val backoffMs: Long = BACKOFF_MS,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var bytesRead = 0L
    private var resumes = 0

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        bytesRead = 0
        resumes = 0
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            try {
                val read = upstream.read(buffer, offset, length)
                if (read > 0) bytesRead += read
                return read
            } catch (e: IOException) {
                if (!isResumable(e) || resumes >= MAX_RESUMES_PER_OPEN || !resume(e)) throw e
                resumes++
            }
        }
    }

    private fun isResumable(e: IOException): Boolean {
        val cause = e.cause
        return e is HttpDataSourceException && e.type == HttpDataSourceException.TYPE_READ &&
            (cause !is InterruptedIOException || cause is SocketTimeoutException) &&
            !Thread.currentThread().isInterrupted && bytesRead > 0
    }

    /** Reopens at [bytesRead]; true once the server continues there, false to give up. */
    private fun resume(cause: IOException): Boolean {
        val original = dataSpec ?: return false
        Log.w(TAG, "Stream broke at byte $bytesRead (${cause.cause ?: cause}), resuming: ${original.uri.path}")
        for (attempt in 1..maxAttempts) {
            closeQuietly()
            try {
                sleep(backoffMs * attempt)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("interrupted while resuming")
            }
            try {
                upstream.open(original.subrange(bytesRead))
            } catch (e: IOException) {
                if (e is InterruptedIOException) throw e
                Log.w(TAG, "Resume attempt $attempt of $maxAttempts at byte $bytesRead failed: $e")
                continue
            }
            if (upstream.responseHeaders.keys.any { "Content-Range".equals(it, ignoreCase = true) }) {
                Log.i(TAG, "Resumed at byte $bytesRead on attempt $attempt")
                return true
            }
            Log.w(TAG, "The server ignored the range request, cannot resume at byte $bytesRead")
            closeQuietly()
            return false
        }
        return false
    }

    private fun closeQuietly() {
        try {
            upstream.close()
        } catch (ignored: IOException) {
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        dataSpec = null
        upstream.close()
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ResumingDataSource(upstream.createDataSource())
    }

    companion object {
        private const val TAG = "ResumingDataSource"

        const val MAX_ATTEMPTS = 5
        const val BACKOFF_MS = 1_000L
        const val MAX_RESUMES_PER_OPEN = 10
    }
}
