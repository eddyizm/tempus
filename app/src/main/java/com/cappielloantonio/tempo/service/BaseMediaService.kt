package com.cappielloantonio.tempo.service

import android.annotation.SuppressLint
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.*
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.repository.QueueRepository
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.*
import com.cappielloantonio.tempo.widget.WidgetUpdateManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@UnstableApi
open class BaseMediaService : MediaLibraryService() {
    companion object {
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON =
            "android.media3.session.demo.SHUFFLE_ON"
        private const val CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF =
            "android.media3.session.demo.SHUFFLE_OFF"
        private const val CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF =
            "android.media3.session.demo.REPEAT_OFF"
        private const val CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE =
            "android.media3.session.demo.REPEAT_ONE"
        private const val CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL =
            "android.media3.session.demo.REPEAT_ALL"
        const val ACTION_BIND_EQUALIZER = "com.cappielloantonio.tempo.service.BIND_EQUALIZER"
        const val ACTION_EQUALIZER_UPDATED = "com.cappielloantonio.tempo.service.EQUALIZER_UPDATED"
    }

    protected lateinit var exoplayer: ExoPlayer
    protected lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var networkCallback: CustomNetworkCallback
    private lateinit var equalizerManager: EqualizerManager
    private val widgetUpdateHandler = Handler(Looper.getMainLooper())
    private var widgetUpdateScheduled = false
    private val widgetUpdateRunnable = object : Runnable {
        override fun run() {
            val player = mediaLibrarySession.player
            if (!player.isPlaying) {
                widgetUpdateScheduled = false
                return
            }
            updateWidget(player)
            widgetUpdateHandler.postDelayed(this, WIDGET_UPDATE_INTERVAL_MS)
        }
    }

    private val radioHeaderCheckExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var radioHeaderCheckScheduled = false
    private var radioHeaderCheckFuture: ScheduledFuture<*>? = null
    private val radioHeaderCheckRunnable = Runnable {
        checkRadioHttpHeaders()
    }

    private val binder = LocalBinder()

    open fun playerInitHook() {
        initializeExoPlayer()
        initializeMediaLibrarySession(exoplayer)
        initializePlayerListener(exoplayer)
        setPlayer(null, exoplayer)
    }

    open fun getMediaLibrarySessionCallback(): MediaLibrarySession.Callback {
        return CustomMediaLibrarySessionCallback(baseContext)
    }

    fun updateMediaItems(player: Player) {
        Log.d(javaClass.toString(), "update items")
        val n = player.mediaItemCount
        val k = player.currentMediaItemIndex
        val current = player.currentPosition
        val items = (0..n - 1).map { MappingUtil.mapMediaItem(player.getMediaItemAt(it)) }
        player.clearMediaItems()
        player.setMediaItems(items, k, current)
    }

    fun restorePlayerFromQueue(player: Player) {
        if (player.mediaItemCount > 0) return

        val queueRepository = QueueRepository()
        val storedQueue = queueRepository.media
        if (storedQueue.isNullOrEmpty()) return

        val mediaItems = MappingUtil.mapMediaItems(storedQueue)
        if (mediaItems.isEmpty()) return

        val lastIndex = try {
            queueRepository.lastPlayedMediaIndex
        } catch (_: Exception) {
            0
        }.coerceIn(0, mediaItems.size - 1)

        val lastPosition = try {
            queueRepository.lastPlayedMediaTimestamp
        } catch (_: Exception) {
            0L
        }.let { if (it < 0L) 0L else it }

        player.setMediaItems(mediaItems, lastIndex, lastPosition)
        player.prepare()
        updateWidget(player)
    }

    private var lastRadioArtist: String? = null
    private var lastRadioTitle: String? = null

    fun initializePlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(javaClass.toString(), "onMediaItemTransition" + player.currentMediaItemIndex)
                if (mediaItem == null) return

                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    MediaManager.setLastPlayedTimestamp(mediaItem)
                }
                
                // Restart header checks for radio streams when media item changes
                val mediaType = mediaItem.mediaMetadata.extras?.getString("type")
                if (mediaType == Constants.MEDIA_TYPE_RADIO && player.isPlaying) {
                    stopRadioHeaderChecks()
                    scheduleRadioHeaderChecks()
                } else if (mediaType != Constants.MEDIA_TYPE_RADIO) {
                    stopRadioHeaderChecks()
                }
                
                updateWidget(player)
            }

            override fun onTracksChanged(tracks: Tracks) {
                Log.d(javaClass.toString(), "onTracksChanged " + player.currentMediaItemIndex)
                ReplayGainUtil.setReplayGain(player, tracks)
                val currentMediaItem = player.currentMediaItem
                if (currentMediaItem != null) {
                    val item = MappingUtil.mapMediaItem(currentMediaItem)
                    if (item.mediaMetadata.extras != null)
                        MediaManager.scrobble(item, false)

                    if (player.nextMediaItemIndex == C.INDEX_UNSET)
                        MediaManager.continuousPlay(player.currentMediaItem)
                }

                if (player is ExoPlayer) {
                    // https://stackoverflow.com/questions/56937283/exoplayer-shuffle-doesnt-reproduce-all-the-songs
                    if (MediaManager.justStarted.get()) {
                        Log.d(javaClass.toString(), "update shuffle order")
                        MediaManager.justStarted.set(false)
                        val shuffledList = IntArray(player.mediaItemCount) { i -> i }
                        shuffledList.shuffle()
                        val index = shuffledList.indexOf(player.currentMediaItemIndex)
                        // swap current media index to the first index
                        if (index > -1 && shuffledList.isNotEmpty()) {
                            val tmp = shuffledList[0]
                            shuffledList[0] = shuffledList[index]
                            shuffledList[index] = tmp
                        }
                        player.shuffleOrder =
                            DefaultShuffleOrder(shuffledList, kotlin.random.Random.nextLong())
                    }
                }
            }

            override fun onMetadata(metadata: Metadata) {
                // Handle streaming metadata (ICY, ID3) for radio / streaming content
                val currentItem = player.currentMediaItem ?: return
                val extras = currentItem.mediaMetadata.extras
                val mediaType = extras?.getString("type")
                if (mediaType != Constants.MEDIA_TYPE_RADIO) return

                var artist: String? = null
                var title: String? = null

                for (i in 0 until metadata.length()) {
                    when (val entry = metadata[i]) {
                        is IcyInfo -> {
                            // Common format: "Artist - Title"
                            val icyTitle = entry.title ?: continue
                            val parts = icyTitle.split(" - ", limit = 2)
                            if (parts.size == 2) {
                                artist = parts[0].trim().ifEmpty { null } ?: artist
                                title = parts[1].trim().ifEmpty { null } ?: title
                            } else {
                                title = icyTitle.trim().ifEmpty { null } ?: title
                            }
                        }
                        is TextInformationFrame -> {
                            @Suppress("DEPRECATION")
                            val value = entry.value
                            when (entry.id) {
                                "TPE1" -> if (!value.isNullOrBlank()) {
                                    artist = value
                                }
                                "TIT2" -> if (!value.isNullOrBlank()) {
                                    title = value
                                }
                            }
                        }
                        is VorbisComment -> {
                            // OGG Vorbis/Opus metadata
                            @Suppress("DEPRECATION")
                            val value = entry.value
                            when (entry.key) {
                                "ARTIST" -> if (!value.isNullOrBlank()) {
                                    artist = value
                                }
                                "TITLE" -> if (!value.isNullOrBlank()) {
                                    title = value
                                }
                                "ALBUM" -> {
                                    // Store album if needed, but not used for radio display
                                }
                            }
                        }
                    }
                }

                if (artist.isNullOrBlank() && title.isNullOrBlank()) return

                // Deduplicate consecutive identical metadata
                if (artist == lastRadioArtist && title == lastRadioTitle) return
                lastRadioArtist = artist
                lastRadioTitle = title

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex == C.INDEX_UNSET) return

                val metadataBuilder = currentItem.mediaMetadata.buildUpon()
                val newExtras = Bundle(currentItem.mediaMetadata.extras ?: Bundle())

                artist?.let {
                    metadataBuilder.setArtist(it)
                    newExtras.putString("radioArtist", it)
                }
                title?.let {
                    metadataBuilder.setTitle(it)
                    newExtras.putString("radioTitle", it)
                }

                // Preserve station name separately (fallback to static title if needed)
                if (!newExtras.containsKey("stationName")) {
                    val stationName =
                        currentItem.mediaMetadata.extras?.getString("stationName")
                            ?: currentItem.mediaMetadata.title?.toString()
                    stationName?.let { newExtras.putString("stationName", it) }
                }

                metadataBuilder.setExtras(newExtras)

                val updatedItem = currentItem.buildUpon()
                    .setMediaMetadata(metadataBuilder.build())
                    .build()

                (player as? ExoPlayer)?.let { exo ->
                    exo.replaceMediaItem(currentIndex, updatedItem)
                    updateWidget(exo)
                    // Media3 notification will automatically update via MediaMetadata changes
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(javaClass.toString(), "onIsPlayingChanged " + player.currentMediaItemIndex)
                if (!isPlaying) {
                    MediaManager.setPlayingPausedTimestamp(
                        player.currentMediaItem,
                        player.currentPosition
                    )
                } else {
                    MediaManager.scrobble(player.currentMediaItem, false)
                }
                if (isPlaying) {
                    scheduleWidgetUpdates()
                    scheduleRadioHeaderChecks()
                } else {
                    stopWidgetUpdates()
                    stopRadioHeaderChecks()
                }
                updateWidget(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(javaClass.toString(), "onPlaybackStateChanged")
                super.onPlaybackStateChanged(playbackState)
                if (!player.hasNextMediaItem() &&
                    playbackState == Player.STATE_ENDED &&
                    player.mediaMetadata.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC
                ) {
                    MediaManager.scrobble(player.currentMediaItem, true)
                    MediaManager.saveChronology(player.currentMediaItem)
                }
                updateWidget(player)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d(javaClass.toString(), "onPositionDiscontinuity")
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)

                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    if (oldPosition.mediaItem?.mediaMetadata?.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC) {
                        MediaManager.scrobble(oldPosition.mediaItem, true)
                        MediaManager.saveChronology(oldPosition.mediaItem)
                    }

                    if (newPosition.mediaItem?.mediaMetadata?.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC) {
                        MediaManager.setLastPlayedTimestamp(newPosition.mediaItem)
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                Preferences.setShuffleModeEnabled(shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                Preferences.setRepeatMode(repeatMode)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Log.d(javaClass.toString(), "onAudioSessionIdChanged")
                attachEqualizerIfPossible(audioSessionId)
            }
        })
        if (player.isPlaying) {
            scheduleWidgetUpdates()
        }
    }

    fun setPlayer(oldPlayer: Player?, newPlayer: Player) {
        if (oldPlayer === newPlayer) return
        if (oldPlayer != null) {
            val currentQueue = getQueueFromPlayer(oldPlayer)
            val currentIndex = oldPlayer.currentMediaItemIndex
            val currentPosition = oldPlayer.currentPosition
            val isPlaying = oldPlayer.playWhenReady
            oldPlayer.stop()
            newPlayer.setMediaItems(currentQueue, currentIndex, currentPosition)
            newPlayer.playWhenReady = isPlaying
            newPlayer.prepare()
        }
        mediaLibrarySession.player = newPlayer
    }

    open fun releasePlayers() {
        exoplayer.release()
    }

    fun getQueueFromPlayer(player: Player): List<MediaItem> {
        return (0..player.mediaItemCount - 1).map(player::getMediaItemAt)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession.player

        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()

        playerInitHook()
        initializeEqualizerManager()
        initializeNetworkListener()
        restorePlayerFromQueue(mediaLibrarySession.player)
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        releaseNetworkCallback()
        equalizerManager.release()
        stopWidgetUpdates()
        stopRadioHeaderChecks()
        radioHeaderCheckExecutor.shutdown()
        releasePlayers()
        mediaLibrarySession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Check if the intent is for our custom equalizer binder
        if (intent?.action == ACTION_BIND_EQUALIZER) {
            return binder
        }
        // Otherwise, handle it as a normal MediaLibraryService connection
        return super.onBind(intent)
    }

    private fun initializeExoPlayer() {
        exoplayer = ExoPlayer.Builder(this)
            .setRenderersFactory(getRenderersFactory())
            .setMediaSourceFactory(getMediaSourceFactory())
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(initializeLoadControl())
            .build()

        exoplayer.shuffleModeEnabled = Preferences.isShuffleModeEnabled()
        exoplayer.repeatMode = Preferences.getRepeatMode()
    }

    private fun initializeEqualizerManager() {
        equalizerManager = EqualizerManager()
        val audioSessionId = exoplayer.audioSessionId
        attachEqualizerIfPossible(audioSessionId)
    }

    private fun initializeMediaLibrarySession(player: Player) {
        Log.d(javaClass.toString(), "initializeMediaLibrarySession")
        val sessionActivityPendingIntent =
            TaskStackBuilder.create(this).run {
                addNextIntent(Intent(baseContext, MainActivity::class.java))
                getPendingIntent(0, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
            }

        mediaLibrarySession =
            MediaLibrarySession.Builder(this, player, getMediaLibrarySessionCallback())
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
    }

    private fun initializeNetworkListener() {
        networkCallback = CustomNetworkCallback()
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            networkCallback
        )
        updateMediaItems(mediaLibrarySession.player)
    }

    private fun initializeLoadControl(): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                (DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * Preferences.getBufferingStrategy()).toInt(),
                (DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * Preferences.getBufferingStrategy()).toInt(),
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()
    }

    private fun releaseNetworkCallback() {
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
    }

    private fun updateWidget(player: Player) {
        val mi = player.currentMediaItem
        val title = mi?.mediaMetadata?.title?.toString()
            ?: mi?.mediaMetadata?.extras?.getString("title")
        val artist = mi?.mediaMetadata?.artist?.toString()
            ?: mi?.mediaMetadata?.extras?.getString("artist")
        val album = mi?.mediaMetadata?.albumTitle?.toString()
            ?: mi?.mediaMetadata?.extras?.getString("album")
        val extras = mi?.mediaMetadata?.extras
        val coverId = extras?.getString("coverArtId")
        val songLink = extras?.getString("assetLinkSong")
            ?: AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_SONG, extras?.getString("id"))
        val albumLink = extras?.getString("assetLinkAlbum")
            ?: AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ALBUM, extras?.getString("albumId"))
        val artistLink = extras?.getString("assetLinkArtist")
            ?: AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ARTIST, extras?.getString("artistId"))
        val position = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        WidgetUpdateManager.updateFromState(
            this,
            title ?: "",
            artist ?: "",
            album ?: "",
            coverId,
            player.isPlaying,
            player.shuffleModeEnabled,
            player.repeatMode,
            position,
            duration,
            songLink,
            albumLink,
            artistLink
        )
    }

    private fun scheduleWidgetUpdates() {
        if (widgetUpdateScheduled) return
        widgetUpdateHandler.postDelayed(widgetUpdateRunnable, WIDGET_UPDATE_INTERVAL_MS)
        widgetUpdateScheduled = true
    }

    private fun stopWidgetUpdates() {
        if (!widgetUpdateScheduled) return
        widgetUpdateHandler.removeCallbacks(widgetUpdateRunnable)
        widgetUpdateScheduled = false
    }

    private fun scheduleRadioHeaderChecks() {
        val player = mediaLibrarySession.player
        val currentItem = player.currentMediaItem ?: return
        val mediaType = currentItem.mediaMetadata.extras?.getString("type")
        if (mediaType != Constants.MEDIA_TYPE_RADIO) return
        
        if (radioHeaderCheckScheduled) return
        
        // Check immediately, then periodically
        checkRadioHttpHeaders()
        radioHeaderCheckFuture = radioHeaderCheckExecutor.scheduleWithFixedDelay(
            radioHeaderCheckRunnable,
            RADIO_HEADER_CHECK_INTERVAL_SECONDS,
            RADIO_HEADER_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
        radioHeaderCheckScheduled = true
    }

    private fun stopRadioHeaderChecks() {
        if (!radioHeaderCheckScheduled) return
        radioHeaderCheckFuture?.cancel(false)
        radioHeaderCheckFuture = null
        radioHeaderCheckScheduled = false
    }

    private fun checkRadioHttpHeaders() {
        val player = mediaLibrarySession.player
        val currentItem = player.currentMediaItem ?: return
        val extras = currentItem.mediaMetadata.extras
        val mediaType = extras?.getString("type")
        if (mediaType != Constants.MEDIA_TYPE_RADIO) return
        
        val streamUrl = extras?.getString("uri") ?: currentItem.requestMetadata.mediaUri?.toString()
        if (streamUrl.isNullOrBlank()) return

        try {
            val url = URL(streamUrl)
            val connection = url.openConnection() as? HttpURLConnection ?: run {
                Log.d(javaClass.toString(), "Failed to create HTTP connection for: $streamUrl")
                return
            }
            
            // Try HEAD request first (more efficient)
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("Icy-MetaData", "1")
            connection.setRequestProperty("User-Agent", "Tempus/1.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            try {
                connection.connect()
            } catch (e: Exception) {
                Log.d(javaClass.toString(), "HEAD request failed, trying GET: ${e.message}")
                connection.disconnect()
                // Fallback to GET request with Range header (some servers don't support HEAD)
                val getConnection = url.openConnection() as? HttpURLConnection ?: return
                getConnection.requestMethod = "GET"
                getConnection.setRequestProperty("Icy-MetaData", "1")
                getConnection.setRequestProperty("User-Agent", "Tempus/1.0")
                getConnection.setRequestProperty("Range", "bytes=0-1") // Request minimal data
                getConnection.connectTimeout = 5000
                getConnection.readTimeout = 5000
                
                try {
                    getConnection.connect()
                    val responseCode = getConnection.responseCode
                    if (responseCode >= 400) {
                        Log.d(javaClass.toString(), "GET request failed with code: $responseCode")
                        getConnection.disconnect()
                        return
                    }
                    
                    // Check for various HTTP header formats that contain metadata
                    val streamTitle = getConnection.getHeaderField("icy-name")
                        ?: getConnection.getHeaderField("StreamTitle")
                        ?: getConnection.getHeaderField("stream-title")
                        ?: getConnection.getHeaderField("X-StreamTitle")
                    
                    getConnection.inputStream?.close()
                    getConnection.disconnect()
                    
                    if (streamTitle.isNullOrBlank()) {
                        Log.d(javaClass.toString(), "No HTTP header metadata found in GET response")
                        return
                    }
                    
                    Log.d(javaClass.toString(), "Found HTTP header metadata via GET: $streamTitle")
                    processStreamTitle(streamTitle, player)
                    return
                } catch (e2: Exception) {
                    Log.d(javaClass.toString(), "GET request also failed: ${e2.message}")
                    getConnection.disconnect()
                    return
                }
            }
            
            val responseCode = connection.responseCode
            if (responseCode >= 400) {
                Log.d(javaClass.toString(), "HEAD request failed with code: $responseCode")
                connection.disconnect()
                return
            }
            
            // Check for various HTTP header formats that contain metadata
            // Radio Bob and similar stations send metadata in HTTP headers like:
            // - icy-name: "Artist - Song Title"
            // - StreamTitle: "Artist - Song Title"
            val streamTitle = connection.getHeaderField("icy-name")
                ?: connection.getHeaderField("StreamTitle")
                ?: connection.getHeaderField("stream-title")
                ?: connection.getHeaderField("X-StreamTitle")
            
            connection.disconnect()
            
            if (streamTitle.isNullOrBlank()) {
                Log.d(javaClass.toString(), "No HTTP header metadata found for radio stream")
                return
            }
            
            Log.d(javaClass.toString(), "Found HTTP header metadata via HEAD: $streamTitle")
            processStreamTitle(streamTitle, player)
        } catch (e: Exception) {
            Log.d(javaClass.toString(), "Failed to fetch radio HTTP headers: ${e.message ?: e.javaClass.simpleName}", e)
            // Silently fail - this is a fallback mechanism
        }
    }
    
    private fun processStreamTitle(streamTitle: String, player: Player) {
        // Parse the stream title (could be "Artist - Title" or just "Title")
        // Radio Bob format: "Artist - Song Title"
        var artist: String? = null
        val title: String?
        
        val parts = streamTitle.split(" - ", limit = 2)
        if (parts.size == 2) {
            artist = parts[0].trim().ifEmpty { null }
            title = parts[1].trim().ifEmpty { null }
            Log.d(javaClass.toString(), "Parsed HTTP metadata - Artist: $artist, Title: $title")
        } else {
            title = streamTitle.trim().ifEmpty { null }
            Log.d(javaClass.toString(), "Parsed HTTP metadata - Title only: $title")
        }
        
        if (artist.isNullOrBlank() && title.isNullOrBlank()) return
        
        // Deduplicate consecutive identical metadata
        if (artist == lastRadioArtist && title == lastRadioTitle) {
            Log.d(javaClass.toString(), "Skipping duplicate metadata")
            return
        }
        
        // Update on main thread
        widgetUpdateHandler.post {
            val currentItemNow = player.currentMediaItem ?: return@post
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET) return@post
            
            val currentExtras = currentItemNow.mediaMetadata.extras
            val currentMediaType = currentExtras?.getString("type")
            if (currentMediaType != Constants.MEDIA_TYPE_RADIO) return@post
            
            // Check if we already have metadata from embedded sources (ICY, ID3, etc.)
            // HTTP headers are used as fallback when embedded metadata is not available
            val hasEmbeddedMetadata = !currentItemNow.mediaMetadata.artist.isNullOrBlank() ||
                    !currentItemNow.mediaMetadata.title.isNullOrBlank() ||
                    (currentExtras != null && !currentExtras.getString("radioArtist").isNullOrBlank()) ||
                    (currentExtras != null && !currentExtras.getString("radioTitle").isNullOrBlank())
            
            // Only use HTTP header metadata if we don't have embedded metadata
            // This preserves the original way while adding HTTP header support as fallback
            if (!hasEmbeddedMetadata) {
                Log.d(javaClass.toString(), "Updating radio metadata from HTTP headers - Artist: $artist, Title: $title")
                lastRadioArtist = artist
                lastRadioTitle = title
                
                val metadataBuilder = currentItemNow.mediaMetadata.buildUpon()
                val newExtras = if (currentExtras != null) {
                    Bundle(currentExtras)
                } else {
                    Bundle()
                }
                
                // Set artist and title in MediaMetadata
                // The UI will read these and display "Artist - Title" in the main title label
                artist?.let {
                    metadataBuilder.setArtist(it)
                    newExtras.putString("radioArtist", it)
                }
                title?.let {
                    metadataBuilder.setTitle(it)
                    newExtras.putString("radioTitle", it)
                }
                
                // Preserve station name separately (shown in artist label)
                if (!newExtras.containsKey("stationName")) {
                    val stationName = currentExtras?.getString("stationName")
                        ?: currentItemNow.mediaMetadata.title?.toString()
                    stationName?.let { newExtras.putString("stationName", it) }
                }
                
                metadataBuilder.setExtras(newExtras)
                
                val updatedItem = currentItemNow.buildUpon()
                    .setMediaMetadata(metadataBuilder.build())
                    .build()
                
                (player as? ExoPlayer)?.let { exo ->
                    // replaceMediaItem triggers onMediaMetadataChanged in UI listeners
                    // This will update the player display automatically
                    exo.replaceMediaItem(currentIndex, updatedItem)
                    updateWidget(exo)
                    Log.d(javaClass.toString(), "Radio metadata updated in player")
                }
            } else {
                Log.d(javaClass.toString(), "Skipping HTTP header metadata - embedded metadata already exists")
            }
        }
    }

    private fun attachEqualizerIfPossible(audioSessionId: Int): Boolean {
        if (audioSessionId == 0 || audioSessionId == -1) return false
        val attached = equalizerManager.attachToSession(audioSessionId)
        if (attached) {
            val enabled = Preferences.isEqualizerEnabled()
            equalizerManager.setEnabled(enabled)
            val bands = equalizerManager.getNumberOfBands()
            val savedLevels = Preferences.getEqualizerBandLevels(bands)
            for (i in 0 until bands) {
                equalizerManager.setBandLevel(i.toShort(), savedLevels[i])
            }
            sendBroadcast(Intent(ACTION_EQUALIZER_UPDATED))
        }
        return attached
    }

    private fun getRenderersFactory() = DownloadUtil.buildRenderersFactory(this, false)

    private fun getMediaSourceFactory(): MediaSource.Factory = DynamicMediaSourceFactory(this)

    @UnstableApi
    private class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        private val shuffleCommands: List<CommandButton>
        private val repeatCommands: List<CommandButton>

        constructor(ctx: Context) {
            shuffleCommands = listOf(
                CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON,
                CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF
            )
                .map { getShuffleCommandButton(SessionCommand(it, Bundle.EMPTY), ctx) }
            repeatCommands = listOf(
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF,
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE,
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL
            )
                .map { getRepeatCommandButton(SessionCommand(it, Bundle.EMPTY), ctx) }
        }

        override fun onConnect(
            session: MediaSession,
            controller: ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()

            (shuffleCommands + repeatCommands).forEach { commandButton ->
                commandButton.sessionCommand?.let { availableSessionCommands.add(it) }
            }

            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands.build())
                .setAvailablePlayerCommands(connectionResult.availablePlayerCommands)
                .setMediaButtonPreferences(buildCustomLayout(session.player))
                .build()
            return result
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            Log.d(javaClass.toString(), "onCustomCommand")
            when (customCommand.customAction) {
                CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON -> session.player.shuffleModeEnabled = true
                CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF -> session.player.shuffleModeEnabled = false
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF,
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL,
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE -> {
                    val nextMode = when (session.player.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    session.player.repeatMode = nextMode
                }
            }

            session.setMediaButtonPreferences(buildCustomLayout(session.player))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            Log.d(javaClass.toString(), "onAddMediaItems")
            val updatedMediaItems = mediaItems.map { mediaItem ->
                val mediaMetadata = mediaItem.mediaMetadata
                val newMetadata = mediaMetadata.buildUpon()
                    .setArtist(
                        if (mediaMetadata.artist != null) mediaMetadata.artist
                        else mediaMetadata.extras?.getString("uri") ?: ""
                    )
                    .build()

                mediaItem.buildUpon()
                    .setUri(mediaItem.requestMetadata.mediaUri)
                    .setMediaMetadata(newMetadata)
                    .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                    .build()
            }
            return Futures.immediateFuture(updatedMediaItems)
        }

        @SuppressLint("PrivateResource")
        private fun getShuffleCommandButton(
            sessionCommand: SessionCommand,
            ctx: Context
        ): CommandButton {
            val isOn = sessionCommand.customAction == CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON
            return CommandButton.Builder(if (isOn) CommandButton.ICON_SHUFFLE_OFF else CommandButton.ICON_SHUFFLE_ON)
                .setSessionCommand(sessionCommand)
                .setDisplayName(
                    ctx.getString(
                        if (isOn) R.string.exo_controls_shuffle_on_description
                        else R.string.exo_controls_shuffle_off_description
                    )
                )
                .build()
        }

        @SuppressLint("PrivateResource")
        private fun getRepeatCommandButton(
            sessionCommand: SessionCommand,
            ctx: Context
        ): CommandButton {
            val icon = when (sessionCommand.customAction) {
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
            val description = when (sessionCommand.customAction) {
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE -> R.string.exo_controls_repeat_one_description
                CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL -> R.string.exo_controls_repeat_all_description
                else -> R.string.exo_controls_repeat_off_description
            }
            return CommandButton.Builder(icon)
                .setSessionCommand(sessionCommand)
                .setDisplayName(ctx.getString(description))
                .build()
        }

        private fun buildCustomLayout(player: Player): ImmutableList<CommandButton> {
            val shuffle = shuffleCommands[if (player.shuffleModeEnabled) 1 else 0]
            val repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> repeatCommands[1]
                Player.REPEAT_MODE_ALL -> repeatCommands[2]
                else -> repeatCommands[0]
            }
            return ImmutableList.of(shuffle, repeat)
        }
    }

    private inner class CustomNetworkCallback : ConnectivityManager.NetworkCallback() {
        var wasWifi = false

        init {
            val manager = getSystemService(ConnectivityManager::class.java)
            val network = manager.activeNetwork
            val capabilities = manager.getNetworkCapabilities(network)
            if (capabilities != null)
                wasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            if (isWifi != wasWifi) {
                wasWifi = isWifi
                widgetUpdateHandler.post {
                    updateMediaItems(mediaLibrarySession.player)
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getEqualizerManager(): EqualizerManager {
            return equalizerManager
        }
    }
}

private const val WIDGET_UPDATE_INTERVAL_MS = 1000L
private const val RADIO_HEADER_CHECK_INTERVAL_SECONDS = 10L

