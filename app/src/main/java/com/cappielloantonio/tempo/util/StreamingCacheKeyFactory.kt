package com.cappielloantonio.tempo.util

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory

/**
 * Cache key for the streaming cache based on what identifies the audio bytes
 * (server + song id + requested transcode quality) instead of the full URL.
 *
 * The full URL is a bad key: it carries the auth token/salt and whichever of the
 * two server addresses (local vs public) is currently in use, so the same already
 * cached track becomes a cache miss - and a duplicate cache entry - as soon as any
 * of those change. maxBitRate/format stay part of the key because a different
 * transcode really is different audio.
 */
private const val TAG = "StreamingCacheKeyFactory"

@UnstableApi
class StreamingCacheKeyFactory : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String {
        dataSpec.key?.let { return it }

        val uri = dataSpec.uri
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https") return uri.toString()

        val id = try {
            uri.getQueryParameter("id")
        } catch (exception: UnsupportedOperationException) {
            // opaque uri, cannot be parsed for query parameters
            Log.d(TAG, "Opaque URI, falling back to URL-based cache key", exception)
            null
        } ?: return uri.toString()

        val bitrate = uri.getQueryParameter("maxBitRate").orEmpty()
        val format = uri.getQueryParameter("format").orEmpty()
        val timeOffset = uri.getQueryParameter("timeOffset").orEmpty()
        val server = Preferences.getServerId().orEmpty()

        return "$server|$id|$bitrate|$format|$timeOffset"
    }
}
