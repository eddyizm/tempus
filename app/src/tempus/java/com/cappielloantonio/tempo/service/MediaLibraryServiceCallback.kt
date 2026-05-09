package com.cappielloantonio.tempo.service

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.cappielloantonio.tempo.repository.AutomotiveRepository
import com.cappielloantonio.tempo.repository.QueueRepository
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.MappingUtil
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MediaLibrarySessionCallback"
private val queueSourceCache = ConcurrentHashMap<String, List<MediaItem>>()

@UnstableApi
class MediaLibrarySessionCallback(
    context: Context,
    private val automotiveRepository: AutomotiveRepository
) : BaseSessionCallback(context) {
    init {
        MediaBrowserTree.initialize(context, automotiveRepository)
    }

    // ─────────────────────────────────────────────────────────────
    // Android Auto — browse
    // ─────────────────────────────────────────────────────────────

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        MediaBrowserTree.buildTree()
        return Futures.immediateFuture(LibraryResult.ofItem(MediaBrowserTree.getRootItem(), params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = MediaBrowserTree.getChildren(parentId)

        Log.d(TAG, "onGetChildren parentId = $parentId")

        return Futures.transform(future, { result ->
            val items = result.value ?: emptyList()
            queueSourceCache[Constants.AA_QUEUE_CACHED_SOURCE] = items
            result
        }, MoreExecutors.directExecutor())
    }

    // ─────────────────────────────────────────────────────────────
    // Android Auto — queue resolution
    // ─────────────────────────────────────────────────────────────

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        Log.d(TAG, "onSetMediaItems")
        val firstItem = mediaItems.firstOrNull()
            ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)

        Log.d(TAG, "mediaId = ${firstItem.mediaId}, startIndex = $startIndex, startPositionMs = $startPositionMs")

        if (isRadio(firstItem)) {
            QueueRepository().deleteAll()
            return super.onSetMediaItems(mediaSession, controller, mediaItems, 0, 0)
        }

        val futureQueue = resolveQueueForItem(firstItem, mediaItems)

        return Futures.transform(
            futureQueue,
            { resolvedItems ->
                if (!resolvedItems.isNullOrEmpty()) {
                    val resolvedItemsUntagged = resolvedItems.map { detagForQueue(it) }
                    val children = resolvedItemsUntagged.mapNotNull { MappingUtil.mapToChild(it) }
                    if (children.isNotEmpty()) QueueRepository().insertAll(children, true, 0)
                }
                MediaSession.MediaItemsWithStartPosition(
                    resolvedItems ?: emptyList(),
                    startIndex,
                    startPositionMs
                )
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun detagForQueue(item: MediaItem): MediaItem {
        val extras = item.mediaMetadata.extras?.let { Bundle(it) } ?: Bundle()
        extras.remove("parent_id")
        return item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        Log.d(TAG, "onAddMediaItems")
        val firstItem = mediaItems.firstOrNull() ?: return Futures.immediateFuture(mediaItems)

        Log.d(TAG, "mediaId = ${firstItem.mediaId}")
        val extras = firstItem.requestMetadata.extras ?: firstItem.mediaMetadata.extras
        Log.d(TAG, "extras: ${extras?.keySet()?.joinToString { key -> "$key=${extras.getString(key)}" } ?: "null"}")

        if (isRadio(firstItem)) {
            Log.d(TAG, "Radio")
            return fetchRadioItem(firstItem)
        }

        return resolveQueueForItem(firstItem, mediaItems)
    }

    private fun isRadio(item: MediaItem): Boolean {
        return item.mediaId.startsWith("ir-") ||
                item.mediaMetadata.extras?.getString("type", "") == Constants.MEDIA_TYPE_RADIO ||
                item.requestMetadata.extras?.getString("type", "") == Constants.MEDIA_TYPE_RADIO
    }

    private fun fetchRadioItem(firstItem: MediaItem): ListenableFuture<List<MediaItem>> {
        return Futures.transformAsync(
            automotiveRepository.internetRadioStations,
            { result ->
                val selected = result?.value?.find { it.mediaId == firstItem.mediaId }
                if (selected != null) {
                    val updated = selected.buildUpon()
                        .setMimeType(selected.localConfiguration?.mimeType)
                        .build()
                    Futures.immediateFuture(listOf(updated))
                } else {
                    Futures.immediateFuture(emptyList())
                }
            },
            androidx.core.content.ContextCompat.getMainExecutor(context)
        )
    }

    private fun resolveQueueForItem(
        firstItem: MediaItem,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        Log.d(TAG, "Resolve queue for item")

        val extras = firstItem.requestMetadata.extras ?: firstItem.mediaMetadata.extras
        val parentId = extras?.getString("parent_id")

        val futureQueue: ListenableFuture<List<MediaItem>> = when {
            parentId?.startsWith(Constants.AA_QUEUE_CACHED_SOURCE) == true -> {
                Log.d(TAG, "Fetching AA list source tracks for $parentId")
                val cachedItems = queueSourceCache[Constants.AA_QUEUE_CACHED_SOURCE] ?: emptyList()
                Futures.immediateFuture(cachedItems)
            }

            firstItem.mediaId.startsWith(Constants.AA_INSTANTMIX_SOURCE) -> {
                Log.d(TAG, "Fetching instant mix for $firstItem.mediaId")

                val withoutPrefix = firstItem.mediaId.removePrefix(Constants.AA_INSTANTMIX_SOURCE)
                val countStr = withoutPrefix.substringAfter("[").substringBefore("]")
                val artistId = withoutPrefix.substringAfter("]")
                val count = countStr.toIntOrNull() ?: automotiveRepository.INSTANT_MIX_NUMBER_OF_TRACKS_IN_SMALL_MIX

                // connect handle
                MediaServiceExtensionRegistry.handler = TracksChangedExtension(automotiveRepository)

                Futures.transform(
                    automotiveRepository.getInstantMix(artistId, count),
                    { it.value ?: emptyList() },
                    MoreExecutors.directExecutor()
                )
            }

            firstItem.mediaId.startsWith(Constants.AA_MADE_FOR_YOU_SOURCE) -> {
                Log.d(TAG, "Fetching MadeForYou for $firstItem.mediaId")

                val withoutPrefix = firstItem.mediaId.removePrefix(Constants.AA_MADE_FOR_YOU_SOURCE)
                val countStr = withoutPrefix.substringAfter("[").substringBefore("]")
                val mixType = withoutPrefix.substringAfter("]")
                val count = countStr.toIntOrNull() ?: automotiveRepository.INSTANT_MIX_NUMBER_OF_TRACKS_IN_SMALL_MIX

                // connect handle
                MediaServiceExtensionRegistry.handler = TracksChangedExtension(automotiveRepository)

                Futures.transform(
                    automotiveRepository.getMadeForYou(mixType, count),
                    { it.value ?: emptyList() },
                    MoreExecutors.directExecutor()
                )
            }

            else -> {
                Log.d(TAG, "Fallback queue for item ${firstItem.mediaId}")
                val resolvedItems = ArrayList<MediaItem>()
                mediaItems.forEach { item ->
                    val sessionItem = item.localConfiguration?.uri?.let { item }
                        ?: automotiveRepository.getSessionMediaItem(item.mediaId)?.let { session ->
                            automotiveRepository.getMetadatas(session.timestamp!!)
                        }
                    sessionItem?.let { resolved ->
                        when (resolved) {
                            is List<*> -> resolvedItems.addAll(resolved.filterIsInstance<MediaItem>())
                            is MediaItem -> resolvedItems.add(resolved)
                            else -> { /* ignore */ }
                        }
                    }
                }
                if (resolvedItems.isEmpty()) resolvedItems.add(firstItem)
                Futures.immediateFuture(resolvedItems)
            }
        }

        return Futures.transform(
            futureQueue,
            { resolvedItems ->
                if (resolvedItems.isEmpty()) return@transform resolvedItems

                val startIndex = resolvedItems.indexOfFirst { it.mediaId == firstItem.mediaId }
                Log.d(TAG, "Start index for clicked item ${firstItem.mediaId} = $startIndex")
                if (startIndex <= 0) return@transform resolvedItems

                val children = resolvedItems.mapNotNull { MappingUtil.mapToChild(it) }
                if (children.isNotEmpty()) {
                    QueueRepository().insertAll(children, true, 0)
                }

                val firstResolved = resolvedItems[0]
                val extras = (firstResolved.mediaMetadata.extras ?: Bundle()).apply {
                    putInt(Constants.AA_START_INDEX, startIndex)
                }
                val newFirstResolved = firstResolved.buildUpon()
                    .setMediaMetadata(firstResolved.mediaMetadata.buildUpon().setExtras(extras).build())
                    .build()

                val updatedResolved = resolvedItems.toMutableList()
                updatedResolved[0] = newFirstResolved
                updatedResolved
            },
            MoreExecutors.directExecutor()
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Android Auto — search
    // ─────────────────────────────────────────────────────────────

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 60, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return MediaBrowserTree.search(query)
    }
}
