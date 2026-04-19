package com.cappielloantonio.tempo.service

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.repository.AutomotiveRepository
import com.cappielloantonio.tempo.repository.QueueRepository
import com.cappielloantonio.tempo.subsonic.base.ApiResponse
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_HEART_LOADING
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_HEART_OFF
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_HEART_ON
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF
import com.cappielloantonio.tempo.util.Constants.CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON
import com.google.common.collect.ImmutableList
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.MappingUtil
import com.cappielloantonio.tempo.util.Preferences
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private const val TAG = "MediaLibraryServiceCallback"
@UnstableApi
open class MediaLibrarySessionCallback(
    private val context: Context,
    private val automotiveRepository: AutomotiveRepository
) :
    MediaLibraryService.MediaLibrarySession.Callback {

    init {
        MediaBrowserTree.initialize(context, automotiveRepository)
    }

    private val customCommandToggleShuffleModeOn = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.exo_controls_shuffle_on_description))
        .setSessionCommand(
            SessionCommand(
                CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON, Bundle.EMPTY
            )
        ).setIconResId(R.drawable.exo_icon_shuffle_off).build()

    private val customCommandToggleShuffleModeOff = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.exo_controls_shuffle_off_description))
        .setSessionCommand(
            SessionCommand(
                CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF, Bundle.EMPTY
            )
        ).setIconResId(R.drawable.exo_icon_shuffle_on).build()

    private val customCommandToggleRepeatModeOff = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.exo_controls_repeat_off_description))
        .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF, Bundle.EMPTY))
        .setIconResId(R.drawable.exo_icon_repeat_off)
        .build()

    private val customCommandToggleRepeatModeOne = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.exo_controls_repeat_one_description))
        .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE, Bundle.EMPTY))
        .setIconResId(R.drawable.exo_icon_repeat_one)
        .build()

    private val customCommandToggleRepeatModeAll = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.exo_controls_repeat_all_description))
        .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL, Bundle.EMPTY))
        .setIconResId(R.drawable.exo_icon_repeat_all)
        .build()

    private val customCommandToggleHeartOn = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.exo_controls_heart_on_description))
        .setSessionCommand(
            SessionCommand(
                CUSTOM_COMMAND_TOGGLE_HEART_ON, Bundle.EMPTY
            )
        )
        .setIconResId(R.drawable.ic_favorite)
        .build()

    private val customCommandToggleHeartOff = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.exo_controls_heart_off_description))
        .setSessionCommand(
            SessionCommand(CUSTOM_COMMAND_TOGGLE_HEART_OFF, Bundle.EMPTY)
        )
        .setIconResId(R.drawable.ic_favorites_outlined)
        .build()

    // Fake Command while waiting for like update command
    private val customCommandToggleHeartLoading = CommandButton.Builder()
        .setDisplayName(context.getString(R.string.cast_expanded_controller_loading))
        .setSessionCommand(
            SessionCommand(CUSTOM_COMMAND_TOGGLE_HEART_LOADING, Bundle.EMPTY)
        )
        .setIconResId(R.drawable.ic_bookmark_sync)
        .build()

    private val customLayoutCommandButtons = listOf(
        customCommandToggleShuffleModeOn,
        customCommandToggleShuffleModeOff,
        customCommandToggleRepeatModeOff,
        customCommandToggleRepeatModeOne,
        customCommandToggleRepeatModeAll,
        customCommandToggleHeartOn,
        customCommandToggleHeartOff,
        customCommandToggleHeartLoading,
    )

    @OptIn(UnstableApi::class)
    val mediaNotificationSessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .also { builder ->
                customLayoutCommandButtons.forEach { commandButton ->
                    commandButton.sessionCommand?.let { builder.add(it) }
                }
            }.build()

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession, controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        session.player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateMediaNotificationCustomLayout(session)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateMediaNotificationCustomLayout(session)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateMediaNotificationCustomLayout(session)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateMediaNotificationCustomLayout(session)
            }
        })

        // FIXME: I'm not sure this if is required anymore
        if (session.isMediaNotificationController(controller) || session.isAutomotiveController(
                controller
            ) || session.isAutoCompanionController(controller)
        ) {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(mediaNotificationSessionCommands)
                .setCustomLayout(buildCustomLayout(session.player))
                .build()
        }

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
    }

    // Update the mediaNotification after some changes
    @OptIn(UnstableApi::class)
    private fun updateMediaNotificationCustomLayout(
        session: MediaSession,
        isRatingPending: Boolean = false
    ) {
        session.setCustomLayout(
            session.mediaNotificationControllerInfo!!,
            buildCustomLayout(session.player, isRatingPending)
        )
    }

    private fun buildCustomLayout(player: Player, isRatingPending: Boolean = false): ImmutableList<CommandButton> {
        val customLayout = mutableListOf<CommandButton>()

        val showShuffle = Preferences.showShuffleInsteadOfHeart()

        if (!showShuffle) {
            if (player.currentMediaItem != null && !isRatingPending) {
                if ((player.mediaMetadata.userRating as HeartRating?)?.isHeart == true) {
                    customLayout.add(customCommandToggleHeartOn)
                } else {
                    customLayout.add(customCommandToggleHeartOff)
                }
            }
        } else {
            customLayout.add(
                if (player.shuffleModeEnabled) customCommandToggleShuffleModeOff else customCommandToggleShuffleModeOn
            )
        }

        // Add repeat button
        val repeatButton = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> customCommandToggleRepeatModeOne
            Player.REPEAT_MODE_ALL -> customCommandToggleRepeatModeAll
            else -> customCommandToggleRepeatModeOff
        }

        customLayout.add(repeatButton)
        return ImmutableList.copyOf(customLayout)
    }

    // Setting rating without a mediaId will set the currently listened mediaId
    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        return onSetRating(session, controller, session.player.currentMediaItem!!.mediaId, rating)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        val isStaring = (rating as HeartRating).isHeart

        val networkCall = if (isStaring)
            App.getSubsonicClientInstance(false)
                .mediaAnnotationClient
                .star(mediaId, null, null)
        else
            App.getSubsonicClientInstance(false)
                .mediaAnnotationClient
                .unstar(mediaId, null, null)

        return CallbackToFutureAdapter.getFuture { completer ->
            networkCall.enqueue(object : Callback<ApiResponse?> {
                @OptIn(UnstableApi::class)
                override fun onResponse(
                    call: Call<ApiResponse?>,
                    response: Response<ApiResponse?>
                ) {
                    if (response.isSuccessful) {

                        // Search if the media item in the player should be updated
                        for (i in 0 until session.player.mediaItemCount) {
                            val mediaItem = session.player.getMediaItemAt(i)
                            if (mediaItem.mediaId == mediaId) {
                                val newMetadata = mediaItem.mediaMetadata.buildUpon()
                                    .setUserRating(HeartRating(isStaring)).build()
                                session.player.replaceMediaItem(
                                    i,
                                    mediaItem.buildUpon().setMediaMetadata(newMetadata).build()
                                )
                            }
                        }

                        updateMediaNotificationCustomLayout(session)
                        completer.set(SessionResult(SessionResult.RESULT_SUCCESS))
                    } else {
                        updateMediaNotificationCustomLayout(session)
                        completer.set(
                            SessionResult(
                                SessionError(
                                    response.code(),
                                    response.message()
                                )
                            )
                        )
                    }
                }

                @OptIn(UnstableApi::class)
                override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                    updateMediaNotificationCustomLayout(session)
                    completer.set(
                        SessionResult(
                            SessionError(
                                SessionError.ERROR_UNKNOWN,
                                "An error as occurred"
                            )
                        )
                    )
                }
            })
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {

        when (customCommand.customAction) {
            CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON -> {
                session.player.shuffleModeEnabled = true
                updateMediaNotificationCustomLayout(session)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF -> {
                session.player.shuffleModeEnabled = false
                updateMediaNotificationCustomLayout(session)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF,
            CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL,
            CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE -> {
                val nextMode = when (session.player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                session.player.repeatMode = nextMode
                updateMediaNotificationCustomLayout(session)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CUSTOM_COMMAND_TOGGLE_HEART_ON,
            CUSTOM_COMMAND_TOGGLE_HEART_OFF -> {
                val currentRating = session.player.mediaMetadata.userRating as? HeartRating
                val isCurrentlyLiked = currentRating?.isHeart ?: false

                val newLikedState = !isCurrentlyLiked

                updateMediaNotificationCustomLayout(
                    session,
                    isRatingPending = true // Show loading state
                )
                return onSetRating(session, controller, HeartRating(newLikedState))
            }
            else -> return Futures.immediateFuture(
                SessionResult(
                    SessionError(
                        SessionError.ERROR_NOT_SUPPORTED,
                        customCommand.customAction
                    )
                )
            )
        }
    }

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
        return MediaBrowserTree.getChildren(parentId)
    }

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

        Log.d(TAG, "mediaId = ${firstItem.mediaId},  startIndex = $startIndex, startPositionMs = $startPositionMs")

        if (isRadio(firstItem)) {
            QueueRepository().deleteAll()
            return super.onSetMediaItems(mediaSession, controller, mediaItems, 0, 0)
        }

        val futureQueue = resolveQueueForItem(firstItem, mediaItems)

        return Futures.transform(
            futureQueue,
            { resolvedItems ->
                if (!resolvedItems.isNullOrEmpty()) {
                    val children = resolvedItems.mapNotNull { MappingUtil.mapToChild(it) }
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

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        Log.d(TAG, "onAddMediaItems")
        val firstItem = mediaItems.firstOrNull() ?: return Futures.immediateFuture(mediaItems)

        Log.d(TAG, "mediaId = ${firstItem.mediaId}")
        val extras = firstItem.requestMetadata.extras ?: firstItem.mediaMetadata.extras
        Log.d(TAG, "extras: ${extras?.keySet()?.joinToString { key -> "$key=${extras.get(key)}" } ?: "null"}")

        if (isRadio(firstItem)) {
            Log.d(TAG, "Radio")
            return fetchRadioItem(firstItem)
        }

        return resolveQueueForItem(firstItem, mediaItems)
    }

    private fun isRadio(item: MediaItem): Boolean {
        return item.mediaId?.startsWith("ir-") == true ||
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
            parentId?.startsWith(Constants.AA_ALBUM_SOURCE) == true -> {
                Log.d(TAG, "Fetching album tracks for $parentId")
                Futures.transform(
                    automotiveRepository.getAlbumTracks(parentId.removePrefix(Constants.AA_ALBUM_SOURCE)),
                    { it.value ?: emptyList() },
                    MoreExecutors.directExecutor()
                )
            }

            parentId?.startsWith(Constants.AA_PLAYLIST_SOURCE) == true -> {
                Log.d(TAG, "Fetching playlist tracks for $parentId")
                Futures.transform(
                    automotiveRepository.getPlaylistSongs(parentId.removePrefix(Constants.AA_PLAYLIST_SOURCE)),
                    { it.value ?: emptyList() },
                    MoreExecutors.directExecutor()
                )
            }

            firstItem.mediaId?.startsWith(Constants.AA_INSTANTMIX_SOURCE) == true -> {
                Log.d(TAG, "Fetching instant mix for $firstItem.mediaId")
                Futures.transform(
                    automotiveRepository.getInstantMix(firstItem.mediaId.removePrefix(Constants.AA_INSTANTMIX_SOURCE), 12),
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
                    sessionItem?.let { resolvedItems.addAll(if (it is List<*>) it as List<MediaItem> else listOf(it as MediaItem)) }
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
