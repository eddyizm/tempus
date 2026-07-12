package com.cappielloantonio.tempo.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.DownloadUtil
import com.cappielloantonio.tempo.util.Preferences
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pre-caches the next tracks of the play queue into the streaming cache, so
 * skips start instantly and playback survives short connectivity drops. The
 * player reads through the same cache with the same keys, so anything written
 * here is picked up transparently.
 *
 * Runs only when the user enabled it (settings > data), never for radio
 * streams or already-downloaded tracks, and by default only on unmetered
 * networks.
 */
@UnstableApi
object QueuePreloader {
    private const val TAG = "QueuePreloader"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "queue-preloader").apply { priority = Thread.MIN_PRIORITY }
    }
    private val generation = AtomicInteger(0)

    @Volatile
    private var activeWriter: CacheWriter? = null

    /** Must be called from the player's application thread. */
    fun preload(context: Context, player: Player) {
        val count = Preferences.getPrecacheTracksCount()
        if (count <= 0) return

        val appContext = context.applicationContext

        if (Preferences.isPrecacheWifiOnly() && isMetered(appContext)) {
            cancel()
            return
        }

        val uris = collectUpcomingStreamUris(appContext, player, count)

        val myGeneration: Int
        synchronized(this) {
            myGeneration = generation.incrementAndGet()
            activeWriter?.cancel()
        }

        if (uris.isEmpty()) return

        executor.execute {
            for (uri in uris) {
                if (generation.get() != myGeneration) return@execute
                cacheTrack(appContext, uri, myGeneration)
            }
        }
    }

    fun cancel() {
        synchronized(this) {
            generation.incrementAndGet()
            activeWriter?.cancel()
        }
    }

    private fun collectUpcomingStreamUris(context: Context, player: Player, count: Int): List<Uri> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return emptyList()

        val uris = ArrayList<Uri>(count)
        var index = currentIndex

        while (uris.size < count) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == currentIndex) break

            val mediaItem = player.getMediaItemAt(index)

            val type = mediaItem.mediaMetadata.extras?.getString("type")
            if (type == Constants.MEDIA_TYPE_RADIO || mediaItem.mediaId.startsWith("ir-")) continue

            if (DownloadUtil.getDownloadTracker(context).isDownloaded(mediaItem.mediaId)) continue

            val uri = mediaItem.localConfiguration?.uri
                ?: mediaItem.requestMetadata.mediaUri
                ?: continue

            val scheme = uri.scheme
            if (scheme != "http" && scheme != "https") continue

            uris.add(uri)
        }

        return uris
    }

    private fun cacheTrack(context: Context, uri: Uri, myGeneration: Int) {
        val dataSpec = DataSpec.Builder().setUri(uri).build()
        val dataSource = DownloadUtil.getStreamingCacheWriterFactory(context).createDataSource()
        val writer = CacheWriter(dataSource, dataSpec, null, null)

        // Re-check the generation while holding the same lock cancel() takes:
        // otherwise a cancel() landing between the caller's generation check and
        // this assignment would miss the writer and the stale write would run to
        // completion.
        synchronized(this) {
            if (generation.get() != myGeneration) return
            activeWriter = writer
        }

        try {
            writer.cache()
            Log.d(TAG, "Pre-cached $uri")
        } catch (exception: Exception) {
            Log.d(TAG, "Pre-cache aborted for $uri: $exception")
            removePartialResource(context, dataSource.cacheKeyFactory.buildCacheKey(dataSpec))
        } finally {
            synchronized(this) {
                if (activeWriter === writer) activeWriter = null
            }
        }
    }

    /**
     * Same policy as StreamingCacheDataSource.close(): a resource whose total
     * length never became known is an unfinished transcode fragment that can't
     * be safely resumed with a range request, so drop it.
     */
    private fun removePartialResource(context: Context, cacheKey: String) {
        try {
            val cache = DownloadUtil.getStreamingCacheForPreload(context)
            val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey))
            if (contentLength == C.LENGTH_UNSET.toLong()) {
                cache.removeResource(cacheKey)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to clean up partial cache entry $cacheKey: $exception")
        }
    }

    private fun isMetered(context: Context): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        return connectivityManager.isActiveNetworkMetered
    }
}
