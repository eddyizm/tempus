package com.eddyizm.tempus.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import com.eddyizm.tempus.model.Download as RoomDownload
import com.eddyizm.tempus.repository.DownloadRepository
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.DownloadUtil
import com.eddyizm.tempus.util.ExternalAudioReader
import com.eddyizm.tempus.util.MusicUtil
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.util.StreamingCacheKeyFactory
import java.util.concurrent.Executors

@UnstableApi
object AutoDownloadPromoter {
    private const val TAG = "AutoDownloadPromoter"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "auto-download-promoter").apply { priority = Thread.MIN_PRIORITY }
    }

    /**
     * Promotes a fully streamed track from [streamingCache] into permanent storage (Path A or Path B).
     * Runs completely in the background on a low-priority thread with 0 network usage.
     */
    fun promoteIfEligible(context: Context, child: Child?, streamUri: Uri?, trigger: String = "unknown") {
        if (child == null || streamUri == null) {
            Log.i(TAG, "Skipping promotion: missing child or stream URI (trigger=$trigger)")
            return
        }
        if (!Preferences.isAutoDownloadPlayedTracks()) {
            Log.i(TAG, "Skipping ${child.id}: auto-download preference is disabled (trigger=$trigger)")
            return
        }

        // Never auto-download radio streams
        val type = child.type
        if (type == Constants.MEDIA_TYPE_RADIO || child.id.startsWith("ir-")) {
            Log.i(TAG, "Skipping radio stream ${child.id} (trigger=$trigger)")
            return
        }

        Log.i(TAG, "Promotion requested for ${child.id} (trigger=$trigger, path=${streamUri.path})")

        val appContext = context.applicationContext

        executor.execute {
            try {
                promoteInternal(appContext, child, streamUri, trigger)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to auto-download ${child.id} (trigger=$trigger)", exception)
            }
        }
    }

    private fun promoteInternal(context: Context, child: Child, streamUri: Uri, trigger: String) {
        // 1. Check if already downloaded
        val downloadTracker = DownloadUtil.getDownloadTracker(context)
        if (downloadTracker.isDownloaded(child.id)) {
            Log.i(TAG, "Skipping ${child.id}: already downloaded in Path A")
            return
        }

        if (Preferences.getDownloadDirectoryUri() != null && ExternalAudioReader.getUri(child) != null) {
            Log.i(TAG, "Skipping ${child.id}: already downloaded in Path B")
            return
        }

        // 2. Check if fully cached in streamingCache
        if (!QueuePreloader.isFullyCached(context, streamUri, child.id)) {
            Log.i(TAG, "Skipping ${child.id}: streaming cache is incomplete (trigger=$trigger)")
            return
        }

        // 3. Promote based on configured download storage preference
        val externalFolderUri = Preferences.getDownloadDirectoryUri()
        Log.i(TAG, "Promoting ${child.id} from complete streaming cache to ${if (externalFolderUri == null) "Path A" else "Path B"}")
        if (externalFolderUri == null) {
            promoteToPathA(context, child, streamUri)
        } else {
            promoteToPathB(context, child, streamUri, externalFolderUri)
        }
    }

    private fun promoteToPathA(context: Context, child: Child, streamUri: Uri) {
        Log.i(TAG, "Copying ${child.id} from streaming cache to Media3 download cache")

        val sourceDataSource = CacheDataSource.Factory()
            .setCache(DownloadUtil.getStreamingCacheForPreload(context))
            .setCacheKeyFactory(StreamingCacheKeyFactory())
            .setUpstreamDataSourceFactory(null)
            .createDataSource()

        val destDataSource = CacheDataSource.Factory()
            .setCache(DownloadUtil.getDownloadCache(context))
            .setUpstreamDataSourceFactory { sourceDataSource }
            .createDataSource()

        var totalBytesCached = 0L
        val dataSpec = DataSpec.Builder().setUri(streamUri).build()
        val writer = CacheWriter(destDataSource, dataSpec, null) { _, bytesCached, _ ->
            totalBytesCached = bytesCached
        }
        writer.cache()

        val bytes = if (totalBytesCached > 0L) totalBytesCached else (child.size ?: 0L)

        val downloadRequest = DownloadRequest.Builder(child.id, streamUri).build()
        val media3Download = Download(
            downloadRequest,
            Download.STATE_COMPLETED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            bytes,
            0,
            Download.FAILURE_REASON_NONE
        )

        DownloadUtil.getDownloadTracker(context).registerCompletedDownload(media3Download)

        val roomDownload = RoomDownload(child).apply {
            downloadState = 1
            downloadUri = streamUri.toString()
        }
        MusicUtil.applyTranscodedDownloadMetadata(roomDownload)
        DownloadRepository().insert(roomDownload)
        Log.i(TAG, "Promoted ${child.id} to Path A; Room insert queued")
    }

    private fun promoteToPathB(context: Context, child: Child, streamUri: Uri, directoryUriString: String) {
        Log.i(TAG, "Copying ${child.id} from streaming cache to SAF storage")
        val directory = DocumentFile.fromTreeUri(context, Uri.parse(directoryUriString))
        if (directory == null || !directory.canWrite()) {
            Log.w(TAG, "Configured download directory not writable, falling back to Path A")
            promoteToPathA(context, child, streamUri)
            return
        }

        val artist = child.artist.orEmpty()
        val title = child.title ?: child.id
        val album = child.album.orEmpty()
        var baseName = if (artist.isEmpty()) title else "$artist - $title"
        if (album.isNotEmpty()) baseName += " ($album)"
        val sanitized = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "download" }

        val format = streamUri.getQueryParameter("format")?.takeIf { it != "raw" && it.isNotEmpty() }
            ?: child.suffix?.takeIf { it.isNotEmpty() }
            ?: "mp3"
        val mimeType = if (format == "opus") "audio/ogg" else "audio/$format"
        val fileName = "$sanitized.$format"

        val targetFile = directory.createFile(mimeType, fileName)
        if (targetFile == null) {
            Log.w(TAG, "Could not create SAF target file $fileName, falling back to Path A")
            promoteToPathA(context, child, streamUri)
            return
        }

        val sourceDataSource = CacheDataSource.Factory()
            .setCache(DownloadUtil.getStreamingCacheForPreload(context))
            .setCacheKeyFactory(StreamingCacheKeyFactory())
            .setUpstreamDataSourceFactory(null)
            .createDataSource()

        val dataSpec = DataSpec.Builder().setUri(streamUri).build()
        val buffer = ByteArray(16 * 1024)
        sourceDataSource.open(dataSpec)
        try {
            context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
                var read: Int
                while (sourceDataSource.read(buffer, 0, buffer.size).also { read = it } != C.RESULT_END_OF_INPUT) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } finally {
            sourceDataSource.close()
        }

        val roomDownload = RoomDownload(child).apply {
            downloadState = 1
            downloadUri = targetFile.uri.toString()
            suffix = format
            contentType = mimeType
        }
        DownloadRepository().insert(roomDownload)
        ExternalAudioReader.refreshCacheAsync()
        Log.i(TAG, "Promoted ${child.id} to Path B; Room insert queued")
    }
}
