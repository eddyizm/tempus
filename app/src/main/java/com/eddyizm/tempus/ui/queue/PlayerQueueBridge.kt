package com.eddyizm.tempus.ui.queue

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.eddyizm.tempus.R
import com.eddyizm.tempus.lan.LanRemoteSession
import com.eddyizm.tempus.model.Download
import com.eddyizm.tempus.repository.DownloadRepository
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.PlayQueue
import com.eddyizm.tempus.ui.dialog.PlaylistChooserDialog
import com.eddyizm.tempus.ui.fragment.PlayerBottomSheetFragment
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.DownloadUtil
import com.eddyizm.tempus.util.ExternalAudioReader
import com.eddyizm.tempus.util.ExternalAudioWriter
import com.eddyizm.tempus.util.MappingUtil
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.viewmodel.PlaybackViewModel
import com.eddyizm.tempus.viewmodel.PlayerBottomSheetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerQueueBridge @JvmOverloads constructor(
    private val composeView: ComposeView,
    private val fragment: PlayerBottomSheetFragment,
    private val playerBottomSheetViewModel: PlayerBottomSheetViewModel,
    private val playbackViewModel: PlaybackViewModel,
    private val downloadRepository: DownloadRepository = DownloadRepository()
) {
    var isQueueOpen by mutableStateOf(false)
        private set

    private var backCallback: OnBackPressedCallback? = null
    private var sheetHide: (suspend () -> Unit)? = null

    fun init() {
        composeView.visibility = View.GONE
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                closeQueue()
            }
        }
        fragment.requireActivity().onBackPressedDispatcher.addCallback(
            fragment.viewLifecycleOwner,
            backCallback!!
        )

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val rawQueueState by playerBottomSheetViewModel.queueSong.observeAsState(
                    playerBottomSheetViewModel.queueSong.value ?: emptyList()
                )
                val rawChildList = remember(rawQueueState) {
                    rawQueueState.map { it as Child }
                }
                var queueState by remember { mutableStateOf(rawChildList) }
                LaunchedEffect(rawChildList) {
                    val oldIds = queueState.map { it.id }
                    val newIds = rawChildList.map { it.id }
                    if (oldIds != newIds) {
                        queueState = rawChildList
                    }
                }

                val currentItemIndexState by playbackViewModel.currentMediaItemIndex.observeAsState(
                    playbackViewModel.currentMediaItemIndex.value ?: -1
                )
                val currentSongIdState by playbackViewModel.currentSongId.observeAsState(
                    playbackViewModel.currentSongId.value
                )
                val isPlayingState by playbackViewModel.isPlaying.observeAsState(
                    playbackViewModel.isPlaying.value ?: false
                )

                val lanQueue by LanRemoteSession.queueState().observeAsState(emptyList())
                val lanState by LanRemoteSession.state().observeAsState(LanRemoteSession.current())
                val isLanActive = lanState?.active == true

                val liveDownloads by downloadRepository.liveDownload.observeAsState(emptyList())
                val downloadedIds = remember(liveDownloads) {
                    liveDownloads?.mapNotNull { it.id }?.toSet() ?: emptySet()
                }
                val externalRefresh by ExternalAudioReader.getRefreshEvents().observeAsState()

                val currentRawSongs = if (isLanActive) (lanQueue ?: emptyList()) else queueState

                val currentIndex = if (isLanActive) {
                    lanState?.index ?: -1
                } else remember(currentRawSongs, currentItemIndexState, currentSongIdState) {
                    val playerIdx = currentItemIndexState ?: -1
                    if (playerIdx in currentRawSongs.indices &&
                        (currentSongIdState == null || currentRawSongs[playerIdx].id == currentSongIdState)
                    ) {
                        playerIdx
                    } else if (currentSongIdState != null) {
                        val idx = currentRawSongs.indexOfFirst { it.id == currentSongIdState }
                        if (idx >= 0) idx else -1
                    } else {
                        -1
                    }
                }
                val effectiveIsPlaying = if (isLanActive) (lanState?.playing == true) else (isPlayingState ?: false)

                PlayerQueueSheet(
                    isOpen = isQueueOpen,
                    onDismissRequest = { closeQueue() },
                    queueSongs = currentRawSongs,
                    currentIndex = currentIndex,
                    currentSongId = currentSongIdState,
                    isPlaying = effectiveIsPlaying,
                    downloadedIds = downloadedIds,
                    externalRefreshTrigger = externalRefresh,
                    onTrackClick = { clickedIndex, clickedSong, currentSnapshot ->
                        if (LanRemoteSession.isActive()) {
                            val state = LanRemoteSession.current()
                            if (clickedIndex == state.index) {
                                LanRemoteSession.command(if (state.playWhenReady) "pause" else "play")
                            } else {
                                LanRemoteSession.routeEdit("select", clickedIndex, clickedIndex)
                            }
                            return@PlayerQueueSheet
                        }
                        val browserFuture = fragment.mediaBrowserListenableFuture ?: return@PlayerQueueSheet
                        browserFuture.addListener({
                            try {
                                val mediaBrowser = browserFuture.get()
                                val currentIdx = mediaBrowser.currentMediaItemIndex
                                val currentItem = mediaBrowser.currentMediaItem
                                if (currentIdx == clickedIndex && currentItem != null && currentItem.mediaId == clickedSong.id) {
                                    if (mediaBrowser.isPlaying) {
                                        mediaBrowser.pause()
                                    } else {
                                        mediaBrowser.play()
                                    }
                                } else {
                                    if (clickedIndex in 0 until mediaBrowser.mediaItemCount &&
                                        mediaBrowser.getMediaItemAt(clickedIndex).mediaId == clickedSong.id
                                    ) {
                                        mediaBrowser.seekTo(clickedIndex, 0)
                                        mediaBrowser.play()
                                    } else {
                                        MediaManager.startQueue(browserFuture, currentSnapshot, clickedIndex)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PlayerQueueBridge", "Error triggering playback", e)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    },
                    onRemoveTrack = { removeIndex ->
                        if (!LanRemoteSession.isActive()) {
                            queueState = queueState.toMutableList().also { list ->
                                if (removeIndex in list.indices) {
                                    list.removeAt(removeIndex)
                                }
                            }
                        }
                        MediaManager.remove(
                            fragment.mediaBrowserListenableFuture,
                            ArrayList(currentRawSongs),
                            removeIndex
                        )
                    },
                    onSwapTracks = { from, to ->
                        if (LanRemoteSession.isActive()) {
                            MediaManager.swap(
                                fragment.mediaBrowserListenableFuture,
                                currentRawSongs,
                                from,
                                to
                            )
                        } else {
                            // swapDatabase() persists the passed list as the new order,
                            // so it must be the reordered list — not the pre-drag snapshot.
                            val reordered = queueState.toMutableList()
                            if (from in reordered.indices && to in reordered.indices) {
                                val item = reordered.removeAt(from)
                                reordered.add(to, item)
                                queueState = reordered
                                MediaManager.swap(
                                    fragment.mediaBrowserListenableFuture,
                                    ArrayList(reordered),
                                    from,
                                    to
                                )
                            }
                        }
                    },
                    onClearUpcoming = { songs, curIdx ->
                        val browserFuture = fragment.mediaBrowserListenableFuture ?: return@PlayerQueueSheet
                        val startPos = if (curIdx >= 0) curIdx + 1 else 0
                        val endPos = songs.size
                        if (startPos < endPos) {
                            MediaManager.removeRange(browserFuture, songs, startPos, endPos)
                        }
                    },
                    onShuffleUpcoming = { songs, curIdx ->
                        val browserFuture = fragment.mediaBrowserListenableFuture ?: return@PlayerQueueSheet
                        val startPos = if (curIdx >= 0) curIdx + 1 else 0
                        val endPos = songs.size - 1
                        if (startPos < endPos) {
                            val childList = songs.toMutableList()
                            val upcomingSublist = childList.subList(startPos, endPos + 1)
                            upcomingSublist.shuffle()
                            MediaManager.shuffle(browserFuture, childList, startPos, endPos)
                        }
                    },
                    onSaveToPlaylist = ::saveToPlaylist,
                    onDownloadAll = ::downloadAll,
                    onLoadSavedQueue = ::loadSavedQueue,
                    onSheetReady = { sheetHide = it }
                )
            }
        }
    }

    fun openQueue() {
        if (isQueueOpen) return
        composeView.visibility = View.VISIBLE
        isQueueOpen = true
        backCallback?.isEnabled = true
        fragment.updateBottomSheetDraggableState()
    }

    fun closeQueue() {
        if (!isQueueOpen) return
        val hide = sheetHide
        val canAnimate = hide != null &&
            fragment.viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!canAnimate) {
            closeImmediate()
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                hide()
            } catch (_: Exception) {
            } finally {
                closeImmediate()
            }
        }
    }

    private fun closeImmediate() {
        if (!isQueueOpen) return
        isQueueOpen = false
        composeView.visibility = View.GONE
        backCallback?.isEnabled = false
        fragment.updateBottomSheetDraggableState()
    }

    fun toggleQueue() {
        if (isQueueOpen) closeQueue() else openQueue()
    }

    private fun saveToPlaylist(songs: List<Child>) {
        val context = fragment.context ?: return
        if (songs.isEmpty()) {
            Toast.makeText(context, R.string.player_queue_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val bundle = Bundle().apply {
            putParcelableArrayList(Constants.TRACKS_OBJECT, ArrayList(songs))
        }
        PlaylistChooserDialog().apply {
            arguments = bundle
        }.show(fragment.parentFragmentManager, null)
    }

    private fun downloadAll(songs: List<Child>) {
        val context = fragment.context ?: return
        if (songs.isEmpty()) {
            Toast.makeText(context, R.string.player_queue_empty, Toast.LENGTH_SHORT).show()
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var count = 0
            if (Preferences.getDownloadDirectoryUri() == null) {
                val dm = DownloadUtil.getDownloadTracker(context)
                if (dm != null) {
                    val validItems = mutableListOf<androidx.media3.common.MediaItem>()
                    val validModels = mutableListOf<Download>()
                    for (song in songs) {
                        try {
                            val item = MappingUtil.mapMediaItem(song)
                            if (item != null) {
                                validItems.add(item)
                                validModels.add(Download(song).apply {
                                    artist = song.artist
                                    album = song.album
                                    coverArtId = song.coverArtId
                                })
                            }
                        } catch (e: Exception) {
                            Log.e("PlayerQueueBridge", "Skipping unmappable song in downloadAll: ${song.id}", e)
                        }
                    }
                    if (validItems.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            dm.download(validItems, validModels)
                        }
                        count = validItems.size
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val currentContext = fragment.context ?: return@withContext
                        Toast.makeText(currentContext, R.string.notification_download_failed, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            } else {
                for (song in songs) {
                    if (ExternalAudioReader.getUri(song) == null) {
                        ExternalAudioWriter.downloadToUserDirectory(context, song)
                        count++
                    }
                }
            }
            withContext(Dispatchers.Main) {
                val currentContext = fragment.context ?: return@withContext
                val msg = if (count > 0) {
                    currentContext.resources.getQuantityString(R.plurals.songs_download_started, count, count)
                } else {
                    currentContext.getString(R.string.player_queue_all_downloaded)
                }
                Toast.makeText(currentContext, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSavedQueue() {
        if (!Preferences.isSyncronizationEnabled()) return
        val context = fragment.context ?: return
        val liveData = playerBottomSheetViewModel.playQueue
        val observer = object : Observer<PlayQueue?> {
            override fun onChanged(value: PlayQueue?) {
                liveData.removeObserver(this)
                val entries = value?.entries
                if (!entries.isNullOrEmpty()) {
                    val currentId = value?.current
                    val targetIndex = entries.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                    MediaManager.startQueue(fragment.mediaBrowserListenableFuture, entries, targetIndex)
                    Toast.makeText(context, R.string.player_queue_loaded, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, R.string.player_queue_no_saved_queue, Toast.LENGTH_SHORT).show()
                }
            }
        }
        liveData.observe(fragment.viewLifecycleOwner, observer)
    }
}
