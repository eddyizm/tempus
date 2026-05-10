package com.cappielloantonio.tempo.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.subsonic.base.ApiResponse
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.Preferences
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private const val TAG = "BaseSessionCallback"

@UnstableApi
open class BaseSessionCallback(protected val context: Context) :
    MediaLibraryService.MediaLibrarySession.Callback {

    // ─────────────────────────────────────────────────────────────
    // CommandButtons
    // ─────────────────────────────────────────────────────────────

    @SuppressLint("PrivateResource")
    private val customCommandToggleShuffleModeOn =
        CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(context.getString(R.string.exo_controls_shuffle_on_description))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON, Bundle.EMPTY))
            .build()

    @SuppressLint("PrivateResource")
    private val customCommandToggleShuffleModeOff =
        CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
            .setDisplayName(context.getString(R.string.exo_controls_shuffle_off_description))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF, Bundle.EMPTY))
            .build()

    @SuppressLint("PrivateResource")
    private val customCommandToggleRepeatModeOff =
        CommandButton.Builder(CommandButton.ICON_REPEAT_OFF)
            .setDisplayName(context.getString(R.string.exo_controls_repeat_off_description))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF, Bundle.EMPTY))
            .build()

    @SuppressLint("PrivateResource")
    private val customCommandToggleRepeatModeOne =
        CommandButton.Builder(CommandButton.ICON_REPEAT_ONE)
            .setDisplayName(context.getString(R.string.exo_controls_repeat_one_description))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE, Bundle.EMPTY))
            .build()

    @SuppressLint("PrivateResource")
    private val customCommandToggleRepeatModeAll =
        CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
            .setDisplayName(context.getString(R.string.exo_controls_repeat_all_description))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL, Bundle.EMPTY))
            .build()

    @Suppress("DEPRECATION")
    private val customCommandToggleHeartOn =
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(context.getString(R.string.exo_controls_heart_on_description))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_HEART_ON, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_favorite)
            .build()

    @Suppress("DEPRECATION")
    private val customCommandToggleHeartOff =
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(context.getString(R.string.exo_controls_heart_off_description))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_HEART_OFF, Bundle.EMPTY))
            .setIconResId(R.drawable.ic_favorites_outlined)
            .build()

    @Suppress("DEPRECATION")
    private val customCommandToggleHeartLoading =
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(context.getString(R.string.cast_expanded_controller_loading))
            .setSessionCommand(SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_HEART_LOADING, Bundle.EMPTY))
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

    // ─────────────────────────────────────────────────────────────
    // onConnect
    // ─────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
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

        if (session.isMediaNotificationController(controller) ||
            session.isAutomotiveController(controller) ||
            session.isAutoCompanionController(controller)
        ) {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(mediaNotificationSessionCommands)
                .setCustomLayout(buildCustomLayout(session.player))
                .build()
        }

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
    }

    // ─────────────────────────────────────────────────────────────
    // Custom layout
    // ─────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    protected fun updateMediaNotificationCustomLayout(
        session: MediaSession,
        isRatingPending: Boolean = false
    ) {
        session.setCustomLayout(
            session.mediaNotificationControllerInfo!!,
            buildCustomLayout(session.player, isRatingPending)
        )
    }

    protected fun buildCustomLayout(
        player: Player,
        isRatingPending: Boolean = false
    ): ImmutableList<CommandButton> {
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
                if (player.shuffleModeEnabled) customCommandToggleShuffleModeOff
                else customCommandToggleShuffleModeOn
            )
        }

        val repeatButton = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> customCommandToggleRepeatModeOne
            Player.REPEAT_MODE_ALL -> customCommandToggleRepeatModeAll
            else -> customCommandToggleRepeatModeOff
        }
        customLayout.add(repeatButton)

        return ImmutableList.copyOf(customLayout)
    }

    // ─────────────────────────────────────────────────────────────
    // Rating (heart)
    // ─────────────────────────────────────────────────────────────

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        return onSetRating(
            session,
            controller,
            session.player.currentMediaItem!!.mediaId,
            rating
        )
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        val isStarring = (rating as HeartRating).isHeart

        val networkCall = if (isStarring)
            App.getSubsonicClientInstance(false)
                .mediaAnnotationClient
                .star(mediaId, null, null)
        else
            App.getSubsonicClientInstance(false)
                .mediaAnnotationClient
                .unstar(mediaId, null, null)

        val future = SettableFuture.create<SessionResult>()

        networkCall.enqueue(object : Callback<ApiResponse?> {
            @OptIn(UnstableApi::class)
            override fun onResponse(call: Call<ApiResponse?>, response: Response<ApiResponse?>) {
                if (response.isSuccessful) {
                    for (i in 0 until session.player.mediaItemCount) {
                        val mediaItem = session.player.getMediaItemAt(i)
                        if (mediaItem.mediaId == mediaId) {
                            val newMetadata = mediaItem.mediaMetadata.buildUpon()
                                .setUserRating(HeartRating(isStarring)).build()
                            session.player.replaceMediaItem(
                                i,
                                mediaItem.buildUpon().setMediaMetadata(newMetadata).build()
                            )
                        }
                    }
                    updateMediaNotificationCustomLayout(session)
                    future.set(SessionResult(SessionResult.RESULT_SUCCESS))
                } else {
                    updateMediaNotificationCustomLayout(session)
                    future.set(SessionResult(SessionError(response.code(), response.message())))
                }
            }

            @OptIn(UnstableApi::class)
            override fun onFailure(call: Call<ApiResponse?>, t: Throwable) {
                updateMediaNotificationCustomLayout(session)
                future.set(SessionResult(SessionError(SessionError.ERROR_UNKNOWN, "An error has occurred")))
            }
        })

        return future
    }

    // ─────────────────────────────────────────────────────────────
    // Custom commands dispatcher
    // ─────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Log.d(TAG, "onCustomCommand: ${customCommand.customAction}")

        return when (customCommand.customAction) {
            Constants.CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_ON -> {
                session.player.shuffleModeEnabled = true
                updateMediaNotificationCustomLayout(session)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            Constants.CUSTOM_COMMAND_TOGGLE_SHUFFLE_MODE_OFF -> {
                session.player.shuffleModeEnabled = false
                updateMediaNotificationCustomLayout(session)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_OFF,
            Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ONE,
            Constants.CUSTOM_COMMAND_TOGGLE_REPEAT_MODE_ALL -> {
                val nextMode = when (session.player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                session.player.repeatMode = nextMode
                updateMediaNotificationCustomLayout(session)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            Constants.CUSTOM_COMMAND_TOGGLE_HEART_ON,
            Constants.CUSTOM_COMMAND_TOGGLE_HEART_OFF -> {
                val currentRating = session.player.mediaMetadata.userRating as? HeartRating
                val isCurrentlyLiked = currentRating?.isHeart ?: false
                updateMediaNotificationCustomLayout(session, isRatingPending = true)
                onSetRating(session, controller, HeartRating(!isCurrentlyLiked))
            }
            else -> Futures.immediateFuture(
                SessionResult(SessionError(SessionError.ERROR_NOT_SUPPORTED, customCommand.customAction))
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    // onAddMediaItems — basic version (without AA)
    // should be override in MediaLibrarySessionCallback for full Tempus release
    // ─────────────────────────────────────────────────────────────

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        Log.d(TAG, "onAddMediaItems")
        val updatedMediaItems = mediaItems.map { mediaItem ->
            val mediaMetadata = mediaItem.mediaMetadata
            val newMetadata = mediaMetadata.buildUpon()
                .setArtist(
                    mediaMetadata.artist
                        ?: mediaMetadata.extras?.getString("uri")
                        ?: ""
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
}