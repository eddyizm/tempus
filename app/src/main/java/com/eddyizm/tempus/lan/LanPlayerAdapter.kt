package com.eddyizm.tempus.lan

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.PlaybackParameters
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Constants
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Presentation-only Player: the original Media3 controls send commands to the selected receiver. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LanPlayerAdapter : SimpleBasePlayer(Looper.getMainLooper()) {
    fun refresh() = invalidateState()

    override fun getState(): State {
        val remote = LanRemoteSession.current()
        val queue = if (LanRemoteSession.queueIsCurrent()) LanRemoteSession.queueItems() else emptyList()
        val shown = if (queue.isEmpty() && remote.count > 0) listOf(LanRemoteSession.currentSong()) else queue
        val index = if (shown.isEmpty()) C.INDEX_UNSET else if (queue.isEmpty()) 0 else remote.index.coerceIn(shown.indices)
        val playlist = shown.mapIndexed { position, song ->
            val duration = if (position == index) remote.duration else (song.duration ?: 0).toLong() * 1000
            MediaItemData.Builder("${remote.revision}:$position")
                .setMediaItem(MediaItem.Builder().setMediaId(song.id).setMediaMetadata(metadata(song)).build())
                .setIsSeekable(duration > 0).setDurationUs(if (duration > 0) duration * 1000 else C.TIME_UNSET).build()
        }
        val commands = Player.Commands.Builder().addAll(Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_VOLUME).apply {
            if (remote.connected) addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_VOLUME, Player.COMMAND_SET_SPEED_AND_PITCH)
            if (remote.connected && queue.isNotEmpty()) {
                addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                add(Player.COMMAND_SEEK_TO_PREVIOUS)
                if (remote.hasNext) addAll(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT)
                if (remote.hasPrevious) add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
        }.build()
        return State.Builder().setAvailableCommands(commands).setPlaylist(playlist)
            .setCurrentMediaItemIndex(index)
            .setPlayWhenReady(remote.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (shown.isEmpty()) Player.STATE_IDLE else if (!remote.connected) Player.STATE_BUFFERING else Player.STATE_READY)
            .setPlaybackSuppressionReason(if (remote.playWhenReady && !remote.playing) Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS else Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            .setRepeatMode(remote.repeat).setShuffleModeEnabled(remote.shuffle).setVolume(remote.volume)
            .setPlaybackParameters(PlaybackParameters(remote.speed, remote.pitch))
            .setContentPositionMs(PositionSupplier { remote.positionAt(SystemClock.elapsedRealtime()) })
            .build()
    }

    override fun handleSetPlayWhenReady(play: Boolean): ListenableFuture<*> {
        LanRemoteSession.command(if (play) "play" else "pause")
        return Futures.immediateVoidFuture()
    }
    override fun handleSetRepeatMode(mode: Int): ListenableFuture<*> {
        LanRemoteSession.command("repeat", mode.toLong()); return Futures.immediateVoidFuture()
    }
    override fun handleSetShuffleModeEnabled(enabled: Boolean): ListenableFuture<*> {
        LanRemoteSession.command("shuffle", if (enabled) 1 else 0); return Futures.immediateVoidFuture()
    }
    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        LanRemoteSession.command("volume", (volume * 100).toLong()); return Futures.immediateVoidFuture()
    }
    override fun handleSetPlaybackParameters(parameters: PlaybackParameters): ListenableFuture<*> {
        LanRemoteSession.playbackParameters(parameters.speed, parameters.pitch); return Futures.immediateVoidFuture()
    }
    override fun handleSeek(index: Int, position: Long, command: Int): ListenableFuture<*> {
        when (command) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT -> LanRemoteSession.command("next")
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS -> LanRemoteSession.command("previous")
            else -> if (index != LanRemoteSession.current().index) LanRemoteSession.routeEdit("select", index, index)
                else LanRemoteSession.command("seek", if (position == C.TIME_UNSET) 0 else position)
        }
        return Futures.immediateVoidFuture()
    }

    companion object {
        @JvmStatic fun metadata(song: Child): MediaMetadata {
            val extras = Bundle().apply {
                putString("id", song.id); putString("type", Constants.MEDIA_TYPE_MUSIC)
                putString("title", song.title); putString("artist", song.artist)
                putString("albumId", song.albumId); putString("artistId", song.artistId)
                putString("coverArtId", song.coverArtId); putString("suffix", song.suffix)
                putInt("duration", song.duration ?: 0); putInt("bitrate", song.bitrate ?: 0)
            }
            return MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                .setAlbumTitle(song.album).setExtras(extras).build()
        }
    }
}
