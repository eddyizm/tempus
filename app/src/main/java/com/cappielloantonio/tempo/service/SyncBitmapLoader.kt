package com.cappielloantonio.tempo.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Log
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Custom BitmapLoader that returns immediateFuture on cache hits. Works around
// Media3 MediaSessionLegacyStub pushing an initial setMetadata(bitmap=null) to
// the legacy AVRCP bridge whenever loadBitmapFromMetadata returns a non-done
// future — Tesla's Bluetooth stack latches on that null-bitmap push and then
// ignores the later bitmap arrival when the new bitmap is pixel-identical to
// the previous track's (same-album tracks on issue #470).
@UnstableApi
class SyncBitmapLoader(
    private val context: Context
) : BitmapLoader {

    private val cache = LruCache<Uri, Bitmap>(CACHE_SIZE)
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/")
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: return Futures.immediateFailedFuture(
                    IOException("BitmapFactory returned null for ${data.size}B")
                )
            Futures.immediateFuture(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "decodeBitmap failed", e)
            Futures.immediateFailedFuture(e)
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        cache.get(uri)?.let {
            return Futures.immediateFuture(it)
        }
        val future = SettableFuture.create<Bitmap>()
        executor.execute {
            try {
                val bitmap = Glide.with(context)
                    .asBitmap()
                    .load(uri)
                    .submit(MAX_ART_SIZE, MAX_ART_SIZE)
                    .get()
                cache.put(uri, bitmap)
                future.set(bitmap)
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    fun prewarm(uri: Uri) {
        if (cache.get(uri) != null) return
        executor.execute {
            if (cache.get(uri) != null) return@execute
            try {
                val bitmap = Glide.with(context)
                    .asBitmap()
                    .load(uri)
                    .submit(MAX_ART_SIZE, MAX_ART_SIZE)
                    .get()
                cache.put(uri, bitmap)
            } catch (_: Exception) {
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    companion object {
        private const val TAG = "SyncBitmapLoader"
        private const val CACHE_SIZE = 20
        // Cap decode dimensions: album art over AVRCP only needs a modest cover
        // image, and bounding this keeps the LRU cache's worst-case heap small.
        private const val MAX_ART_SIZE = 512
    }
}
