package com.eddyizm.tempus.service

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.eddyizm.tempus.repository.LyricsRepository
import com.eddyizm.tempus.repository.OpenRepository
import com.eddyizm.tempus.subsonic.models.Line
import com.eddyizm.tempus.subsonic.models.LyricsList
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.OpenSubsonicExtensionsUtil
import com.eddyizm.tempus.util.Preferences
import com.google.gson.Gson

private const val UPDATE_INTERVAL_MS = 250L

/**
 * Shows the current synced lyrics line on Android Auto (issue #1143).
 *
 * Android Auto has no lyrics view, so while a car is connected the line being sung replaces the
 * title of the current item and the artist line shows "Title - Artist". Both are restored when
 * the track changes, the line has no text, or the car disconnects. Only the ExoPlayer is
 * touched; Cast and UPnP playback are left alone.
 */
@UnstableApi
class AutomotiveLyricsUpdater(
    private val session: MediaSession,
    private val player: ExoPlayer
) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val lyricsRepository = LyricsRepository()
    private val openRepository = OpenRepository()

    private var songId: String? = null
    private var songIndex = C.INDEX_UNSET
    private var originalTitle: CharSequence? = null
    private var originalArtist: CharSequence? = null
    private var lines: List<Line>? = null
    private var shownLineIdx = -1

    private val updateRunnable = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    fun start() {
        player.addListener(this)
        loadLyrics(player.currentMediaItem)
        onIsPlayingChanged(player.isPlaying)
    }

    fun release() {
        handler.removeCallbacks(updateRunnable)
        player.removeListener(this)
        showLine(-1)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(updateRunnable)
        if (isPlaying) handler.post(updateRunnable)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Our own replaceMediaItem reports the same song as a playlist change.
        if (mediaItem?.mediaId == songId && player.currentMediaItemIndex == songIndex) return
        showLine(-1)
        loadLyrics(mediaItem)
    }

    private fun loadLyrics(mediaItem: MediaItem?) {
        songId = mediaItem?.mediaId
        songIndex = player.currentMediaItemIndex
        originalTitle = mediaItem?.mediaMetadata?.title
        originalArtist = mediaItem?.mediaMetadata?.artist
        lines = null
        shownLineIdx = -1

        val id = songId ?: return
        if (mediaItem?.mediaMetadata?.extras?.getString("type") != Constants.MEDIA_TYPE_MUSIC) return

        val cached = lyricsRepository.getLyrics(id)?.structuredLyrics
        if (!cached.isNullOrEmpty()) {
            lines = getSyncedLines(runCatching { Gson().fromJson(cached, LyricsList::class.java) }.getOrNull())
            return
        }

        if (!OpenSubsonicExtensionsUtil.isSongLyricsExtensionAvailable()) return

        val source = openRepository.getLyricsBySongId(id)
        source.observeForever(object : Observer<LyricsList> {
            override fun onChanged(value: LyricsList) {
                source.removeObserver(this)
                if (songId == id) lines = getSyncedLines(value)
            }
        })
    }

    private fun update() {
        val current = lines
        val carConnected = session.connectedControllers.any {
            session.isAutoCompanionController(it) || session.isAutomotiveController(it)
        }
        if (current == null || session.player !== player || !carConnected
            || !Preferences.isAndroidAutoSyncedLyricsEnabled()) {
            showLine(-1)
            return
        }

        val leadMs = Preferences.getAndroidAutoSyncedLyricsLead()
        showLine(findLineIndex(current, player.currentPosition + leadMs))
    }

    private fun showLine(lineIdx: Int) {
        if (lineIdx == shownLineIdx) return
        // Not the current item: on a track change this restores the song that just ended.
        val index = songIndex
        if (index == C.INDEX_UNSET || index >= player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        if (item.mediaId != songId) return

        shownLineIdx = lineIdx
        val text = lines?.getOrNull(lineIdx)?.value?.trim()
        val title = if (text.isNullOrEmpty()) originalTitle else text
        val artist = if (text.isNullOrEmpty()) originalArtist else formatSongInfo(originalTitle, originalArtist)
        if (title == item.mediaMetadata.title && artist == item.mediaMetadata.artist) return

        player.replaceMediaItem(
            index,
            item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(title).setArtist(artist).build())
                .build()
        )
    }

    companion object {
        fun formatSongInfo(title: CharSequence?, artist: CharSequence?): CharSequence? = when {
            title.isNullOrBlank() -> artist
            artist.isNullOrBlank() -> title
            else -> "$title - $artist"
        }

        fun getSyncedLines(lyricsList: LyricsList?): List<Line>? {
            val structured = lyricsList?.structuredLyrics?.firstOrNull() ?: return null
            if (!structured.synced) return null
            return structured.line?.takeIf { it.isNotEmpty() }
        }

        fun findLineIndex(lines: List<Line>, positionMs: Long): Int {
            var idx = -1
            for (i in lines.indices) {
                val start = lines[i].start ?: continue
                if (start > positionMs) break
                idx = i
            }
            return idx
        }
    }
}
