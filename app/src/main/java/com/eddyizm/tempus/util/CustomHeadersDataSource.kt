package com.eddyizm.tempus.util

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends requests that need the server's custom headers (see [CustomHeaders]) through a separate
 * data source, and everything else through the usual one.
 *
 * `DefaultHttpDataSource` re-sends a request's headers when it follows a redirect, even to another
 * host, so it cannot carry secrets safely. Requests that get headers go through [withHeaders]
 * instead, an OkHttp data source whose network interceptor checks the host on every redirect hop.
 * Requests without headers (every request when none are configured, and radio streams) keep using
 * [plain], so nothing changes for them.
 */
@UnstableApi
class CustomHeadersDataSource private constructor(
    private val plain: DataSource,
    private val withHeaders: DataSource,
    private val needsHeaders: (url: String) -> Boolean,
) : DataSource {

    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        plain.addTransferListener(transferListener)
        withHeaders.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (needsHeaders(dataSpec.uri.toString())) withHeaders else plain
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = current
        current = null
        source?.close()
    }

    class Factory(
        private val plain: DataSource.Factory,
        private val withHeaders: DataSource.Factory,
        private val needsHeaders: (url: String) -> Boolean,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            CustomHeadersDataSource(plain.createDataSource(), withHeaders.createDataSource(), needsHeaders)
    }
}
