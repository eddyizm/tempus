package com.cappielloantonio.tempo.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Observer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import com.cappielloantonio.tempo.interfaces.MediaIndexCallback
import com.cappielloantonio.tempo.model.Chronology
import com.cappielloantonio.tempo.repository.ChronologyRepository
import com.cappielloantonio.tempo.repository.QueueRepository
import com.cappielloantonio.tempo.repository.SongRepository
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode
import com.cappielloantonio.tempo.util.MappingUtil
import com.cappielloantonio.tempo.util.Preferences.isContinuousPlayEnabled
import com.cappielloantonio.tempo.util.Preferences.isInstantMixUsable
import com.cappielloantonio.tempo.util.Preferences.isScrobblingEnabled
import com.cappielloantonio.tempo.util.Preferences.setLastInstantMix
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object MediaManager {
    private const val TAG = "MediaManager"
    private var attachedBrowserRef = WeakReference<MediaBrowser?>(null)
    val justStarted: AtomicBoolean = AtomicBoolean(false)

    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun registerPlaybackObserver(
        browserFuture: ListenableFuture<MediaBrowser>?,
        playbackViewModel: PlaybackViewModel
    ) {
        browserFuture?.onComplete(
            onSuccess = { browser ->
                val current = attachedBrowserRef.get()
                if (current != browser) {
                    browser.addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                                || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                                || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                            ) {
                                val mediaId = player.currentMediaItem?.mediaId
                                val playing = player.playbackState == Player.STATE_READY
                                        && player.playWhenReady

                                playbackViewModel.update(mediaId, playing)
                            }
                        }
                    })

                    val mediaId = browser.getCurrentMediaItem()?.mediaId
                    val playing =
                        browser.getPlaybackState() == Player.STATE_READY && browser.getPlayWhenReady()
                    playbackViewModel.update(mediaId, playing)

                    attachedBrowserRef = WeakReference(browser)
                } else {
                    val mediaId = browser.getCurrentMediaItem()?.mediaId
                    val playing =
                        browser.getPlaybackState() == Player.STATE_READY && browser.getPlayWhenReady()
                    playbackViewModel.update(mediaId, playing)
                }
            },
            onFailure = {
                Log.e(TAG, "Failed to get MediaBrowser instance", it)
            }
        )
    }

    @JvmStatic
    fun onBrowserReleased(released: MediaBrowser?) {
        val attached = attachedBrowserRef.get()
        if (attached == released) {
            attachedBrowserRef.clear()
        }
    }

    @JvmStatic
    fun reset(mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            if (mediaBrowser.isPlaying()) {
                mediaBrowser.pause()
            }

            mediaBrowser.stop()
            mediaBrowser.clearMediaItems()
            clearDatabase()
        }
    }

    @JvmStatic
    fun hide(mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            if (mediaBrowser.isPlaying()) {
                mediaBrowser.pause()
            }
        }
    }

    @JvmStatic
    fun check(mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            if (mediaBrowser.mediaItemCount < 1) {
                val media: MutableList<Child?>? = queueRepository.getMedia()
                if (!media.isNullOrEmpty()) {
                    init(mediaBrowserListenableFuture, media)
                }
            }
        }
    }

    private fun init(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: MutableList<Child?>
    ) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            mediaBrowser.clearMediaItems()
            mediaBrowser.setMediaItems(MappingUtil.mapMediaItems(media))
            mediaBrowser.seekTo(
                queueRepository.getLastPlayedMediaIndex(),
                queueRepository.getLastPlayedMediaTimestamp()
            )
            mediaBrowser.prepare()
        }
    }

    @JvmStatic
    fun startQueue(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: MutableList<Child>,
        startIndex: Int
    ) {
        mediaBrowserListenableFuture?.onComplete { browser ->
            val items = MappingUtil.mapMediaItems(media)

            Handler(Looper.getMainLooper()).post {
                justStarted.set(true)
                browser.setMediaItems(items, startIndex, 0)
                browser.prepare()

                val timelineListener = object : Player.Listener {
                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        val itemCount = browser.mediaItemCount
                        if (itemCount > 0 && startIndex >= 0 && startIndex < itemCount) {
                            browser.seekTo(startIndex, 0)
                            browser.play()
                            browser.removeListener(this)
                        } else {
                            Log.d(
                                TAG,
                                "Cannot start playback: itemCount=$itemCount, startIndex=$startIndex"
                            )
                        }
                    }
                }
                browser.addListener(timelineListener)
            }

            backgroundExecutor.execute {
                Log.d(TAG, "Background: enqueuing to database")
                enqueueDatabase(media, true, 0)
            }
        }
    }

    @JvmStatic
    fun startQueue(mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?, media: Child?) {
        mediaBrowserListenableFuture?.onComplete { browser ->
            justStarted.set(true)
            browser.setMediaItem(MappingUtil.mapMediaItem(media))
            browser.prepare()
            browser.play()
            enqueueDatabase(media, true, 0)
        }
    }

    @JvmStatic
    fun playDownloadedMediaItem(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        mediaItem: MediaItem?
    ) {
        if (mediaItem == null)
            return

        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            justStarted.set(true)
            mediaBrowser.setMediaItem(mediaItem)
            mediaBrowser.prepare()
            mediaBrowser.play()
            clearDatabase()
        }
    }

    @JvmStatic
    fun startRadio(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        internetRadioStation: InternetRadioStation
    ) {
        mediaBrowserListenableFuture?.onComplete { browser ->
            justStarted.set(true)
            browser.setMediaItem(MappingUtil.mapInternetRadioStation(internetRadioStation))
            browser.prepare()
            browser.play()
        }
    }

    @JvmStatic
    fun startPodcast(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        podcastEpisode: PodcastEpisode?
    ) {
        mediaBrowserListenableFuture?.onComplete { browser ->
            justStarted.set(true)
            browser.setMediaItem(MappingUtil.mapMediaItem(podcastEpisode))
            browser.prepare()
            browser.play()
        }
    }

    @JvmStatic
    fun enqueue(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: MutableList<Child>,
        playImmediatelyAfter: Boolean
    ) {
        mediaBrowserListenableFuture?.onComplete { browser ->
            Log.e(TAG, "enqueue")
            if (playImmediatelyAfter && browser.getNextMediaItemIndex() != -1) {
                enqueueDatabase(media, false, browser.getNextMediaItemIndex())
                browser.addMediaItems(
                    browser.getNextMediaItemIndex(),
                    MappingUtil.mapMediaItems(media)
                )
            } else {
                enqueueDatabase(
                    media,
                    false,
                    browser.mediaItemCount
                )
                browser.addMediaItems(MappingUtil.mapMediaItems(media))
            }
        }
    }

    @JvmStatic
    fun enqueue(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: Child?,
        playImmediatelyAfter: Boolean
    ) {
        mediaBrowserListenableFuture?.onComplete { browser ->
            Log.e(TAG, "enqueue")
            if (playImmediatelyAfter && browser.getNextMediaItemIndex() != -1) {
                enqueueDatabase(media, false, browser.getNextMediaItemIndex())
                browser.addMediaItem(
                    browser.getNextMediaItemIndex(),
                    MappingUtil.mapMediaItem(media)
                )
            } else {
                enqueueDatabase(
                    media,
                    false,
                    browser.mediaItemCount
                )
                browser.addMediaItem(MappingUtil.mapMediaItem(media))
            }
        }
    }

    @JvmStatic
    fun shuffle(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: MutableList<Child?>,
        startIndex: Int,
        endIndex: Int
    ) {
        mediaBrowserListenableFuture?.onComplete { browser ->
            Log.e(TAG, "shuffle")
            browser.removeMediaItems(startIndex, endIndex + 1)
            browser.addMediaItems(
                MappingUtil.mapMediaItems(media).subList(startIndex, endIndex + 1)
            )
            swapDatabase(media)
        }
    }

    @JvmStatic
    fun swap(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: MutableList<Child?>?,
        from: Int,
        to: Int
    ) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            Log.e(TAG, "swap")
            mediaBrowser.moveMediaItem(from, to)
            swapDatabase(media)
        }
    }

    @JvmStatic
    fun remove(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: MutableList<Child?>,
        toRemove: Int
    ) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            Log.e(TAG, "remove")
            if (mediaBrowser.mediaItemCount > 1 &&
                mediaBrowser.getCurrentMediaItemIndex() != toRemove
            ) {
                mediaBrowser.removeMediaItem(toRemove)
                removeDatabase(media, toRemove)
            } else {
                removeDatabase(media, -1)
            }
        }
    }

    @JvmStatic
    fun removeRange(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        media: MutableList<Child?>,
        fromItem: Int,
        toItem: Int
    ) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            Log.e(TAG, "remove range")
            mediaBrowser.removeMediaItems(fromItem, toItem)
            removeRangeDatabase(media, fromItem, toItem)
        }
    }

    @JvmStatic
    fun getCurrentIndex(
        mediaBrowserListenableFuture: ListenableFuture<MediaBrowser>?,
        callback: MediaIndexCallback
    ) {
        mediaBrowserListenableFuture?.onComplete { mediaBrowser ->
            callback.onRecovery(
                mediaBrowser.getCurrentMediaItemIndex()
            )
        }
    }

    @JvmStatic
    fun setLastPlayedTimestamp(mediaItem: MediaItem?) {
        if (mediaItem != null) queueRepository.setLastPlayedTimestamp(mediaItem.mediaId)
    }

    @JvmStatic
    fun setPlayingPausedTimestamp(mediaItem: MediaItem?, ms: Long) {
        if (mediaItem != null) queueRepository.setPlayingPausedTimestamp(mediaItem.mediaId, ms)
    }

    @JvmStatic
    fun scrobble(mediaItem: MediaItem?, submission: Boolean) {
        val mediaItemId = mediaItem?.mediaMetadata?.extras?.getString("id") ?: return
        if (isScrobblingEnabled()) {
            songRepository.scrobble(mediaItemId, submission)
        }
    }

    @JvmStatic
    fun continuousPlay(
        mediaItem: MediaItem?,
        existingBrowserFuture: ListenableFuture<MediaBrowser>?
    ) {
        if (mediaItem == null || !isContinuousPlayEnabled() || !isInstantMixUsable()) {
            return
        }

        setLastInstantMix()

        val instantMix = songRepository.getContinuousMix(mediaItem.mediaId, 25)

        instantMix.observeForever(object : Observer<MutableList<Child>?> {
            override fun onChanged(value: MutableList<Child>?) {
                if (value.isNullOrEmpty()) {
                    return
                }

                if (existingBrowserFuture != null) {
                    Log.d(TAG, "Continuous play: adding " + value.size + " tracks")
                    enqueue(existingBrowserFuture, value, true)
                }
                instantMix.removeObserver(this)
            }
        })
    }

    @JvmStatic
    fun saveChronology(mediaItem: MediaItem?) {
        if (mediaItem != null) {
            chronologyRepository.insert(Chronology(mediaItem))
        }
    }

    private val queueRepository: QueueRepository
        get() = QueueRepository()

    private val songRepository: SongRepository
        get() = SongRepository()

    private val chronologyRepository: ChronologyRepository
        get() = ChronologyRepository()

    private fun enqueueDatabase(media: MutableList<Child>?, reset: Boolean, afterIndex: Int) {
        queueRepository.insertAll(media, reset, afterIndex)
    }

    private fun enqueueDatabase(media: Child?, reset: Boolean, afterIndex: Int) {
        queueRepository.insert(media, reset, afterIndex)
    }

    private fun swapDatabase(media: MutableList<Child?>?) {
        queueRepository.insertAll(media, true, 0)
    }

    private fun removeDatabase(media: MutableList<Child?>, toRemove: Int) {
        if (toRemove != -1) {
            media.removeAt(toRemove)
            queueRepository.insertAll(media, true, 0)
        }
    }

    private fun removeRangeDatabase(media: MutableList<Child?>, fromItem: Int, toItem: Int) {
        val toRemove = media.subList(fromItem, toItem)

        media.removeAll(toRemove)

        queueRepository.insertAll(media, true, 0)
    }

    private fun clearDatabase() {
        queueRepository.deleteAll()
    }

    private fun ListenableFuture<MediaBrowser>?.onComplete(callback: (MediaBrowser) -> Unit) {
        onComplete(
            onSuccess = callback,
            onFailure = { it.printStackTrace() }
        )
    }

    private fun ListenableFuture<MediaBrowser>?.onComplete(
        onSuccess: (MediaBrowser) -> Unit,
        onFailure: ((Exception) -> Unit),
    ) {
        this?.addListener({
            try {
                if (isDone) {
                    onSuccess(get())
                }
            } catch (e: ExecutionException) {
                onFailure(e)
            } catch (e: InterruptedException) {
                onFailure(e)
            }
        }, MoreExecutors.directExecutor())
    }
}
