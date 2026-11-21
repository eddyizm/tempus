package com.eddyizm.tempus.util

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations

@UnstableApi
class StreamingCacheDataSource private constructor(
    private val cacheDataSource: CacheDataSource,
): DataSource {
    private val TAG = "StreamingCacheDataSource"

    private var currentDataSpec: DataSpec? = null
    private var bytesRead: Long = 0L
    private var reachedEof: Boolean = false

    class Factory(private val cacheDatasourceFactory: CacheDataSource.Factory): DataSource.Factory {
        override fun createDataSource(): DataSource {
            return StreamingCacheDataSource(cacheDatasourceFactory.createDataSource())
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = cacheDataSource.read(buffer, offset, length)
        if (read == C.RESULT_END_OF_INPUT) {
            reachedEof = true
        } else if (read > 0) {
            bytesRead += read.toLong()
        }
        return read
    }

    override fun addTransferListener(transferListener: TransferListener) {
        return cacheDataSource.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val ret = cacheDataSource.open(dataSpec)
        currentDataSpec = dataSpec
        bytesRead = dataSpec.position
        reachedEof = false
        return ret
    }

    override fun getUri(): Uri? {
        return cacheDataSource.uri
    }

    override fun close() {
        cacheDataSource.close()

        val dataSpec = currentDataSpec

        if (dataSpec != null) {
            val cacheKey = cacheDataSource.cacheKeyFactory.buildCacheKey(dataSpec)
            val streamId = try {
                dataSpec.uri.getQueryParameter("id") ?: "unknown"
            } catch (_: UnsupportedOperationException) {
                "unknown"
            }
            var contentLength = ContentMetadata.getContentLength(cacheDataSource.cache.getContentMetadata(cacheKey))

            // If the stream reached EOF from position 0, we now know the full total length
            // even if the server did not supply Content-Length (chunked transcoding).
            if (contentLength == C.LENGTH_UNSET.toLong() && reachedEof && dataSpec.position == 0L && bytesRead > 0L) {
                try {
                    val mutations = ContentMetadataMutations()
                    ContentMetadataMutations.setContentLength(mutations, bytesRead)
                    cacheDataSource.cache.applyContentMetadataMutations(cacheKey, mutations)
                    contentLength = bytesRead
                    Log.d(TAG, "Finalized unknown-length stream $streamId with $bytesRead bytes")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to finalize unknown-length stream", e)
                }
            }

            if (contentLength == C.LENGTH_UNSET.toLong()) {
                Log.d(TAG, "Removing partial stream cache for $streamId")
                cacheDataSource.cache.removeResource(cacheKey)
            } else {
                Log.d(TAG, "Stream $streamId has known content length ($contentLength bytes)")
            }
        }
    }
}