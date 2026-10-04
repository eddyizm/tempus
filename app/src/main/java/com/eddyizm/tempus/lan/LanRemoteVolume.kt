package com.eddyizm.tempus.lan

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/** Android volume routing for the selected receiver; never adjusts the controller's audio. */
object LanRemoteVolume {
    private var session: MediaSession? = null
    private var provider: VolumeProvider? = null
    private val main = Handler(Looper.getMainLooper())

    fun release() {
        session?.isActive = false
        session?.release()
        session = null
        provider = null
    }

    // This private volume-routing session advertises no search action and is not
    // exposed by the Android Auto browser service; Auto keeps its existing session.
    @android.annotation.SuppressLint("MissingOnPlayFromSearch")
    fun update(context: Context, state: LanRemoteState) {
        if (!state.connected || state.deviceVolumeMax <= 0) {
            release()
            return
        }
        val mediaSession = session ?: MediaSession(context.applicationContext, "Tempus LAN").also {
            it.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (session === it) LanRemoteSession.command("play") }
                override fun onPause() { if (session === it) LanRemoteSession.command("pause") }
                override fun onSkipToNext() { if (session === it) LanRemoteSession.command("next") }
                override fun onSkipToPrevious() { if (session === it) LanRemoteSession.command("previous") }
                override fun onSeekTo(pos: Long) { if (session === it) LanRemoteSession.command("seek", pos) }
                override fun onStop() { if (session === it) LanRemoteSession.disconnect() }
            }, main)
            session = it
        }
        if (provider?.maxVolume != state.deviceVolumeMax) {
            provider = object : VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, state.deviceVolumeMax, state.deviceVolume.coerceIn(0, state.deviceVolumeMax)) {
                override fun onAdjustVolume(direction: Int) {
                    main.post {
                        if (provider === this && session === mediaSession) LanRemoteSession.adjustDeviceVolume(direction)
                    }
                }
                override fun onSetVolumeTo(volume: Int) {
                    main.post {
                        if (provider === this && session === mediaSession) LanRemoteSession.setDeviceVolume(volume)
                    }
                }
            }
            mediaSession.setPlaybackToRemote(provider!!)
        }
        provider!!.setCurrentVolume(state.deviceVolume.coerceIn(0, state.deviceVolumeMax))
        mediaSession.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, state.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, state.artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, state.name)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, state.duration).build())
        mediaSession.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP or
                (if (state.hasNext) PlaybackState.ACTION_SKIP_TO_NEXT else 0L) or
                (if (state.hasPrevious) PlaybackState.ACTION_SKIP_TO_PREVIOUS else 0L))
            .setState(if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                state.position, if (state.playing) state.speed else 0f).build())
        mediaSession.isActive = true
    }

    /** Consume both halves of volume key events, including while reconnecting. */
    @JvmStatic fun dispatch(event: KeyEvent): Boolean {
        if (!LanRemoteSession.isActive()) return false
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            session?.controller?.adjustVolume(direction, AudioManager.FLAG_SHOW_UI)
        }
        return true
    }
}
