package com.eddyizm.tempus.service

import androidx.media3.common.MediaItem
import com.eddyizm.tempus.util.ConstantsAA

sealed interface VoiceRoute {
    data class Artist(val artistId: String) : VoiceRoute
    data class Album(val albumId: String) : VoiceRoute
    data class Songs(val songs: List<MediaItem>) : VoiceRoute
}

object VoiceQueryRouter {

    /**
     * Picks what to play for a voice query from its search results: an exact artist match,
     * then an exact album match, then the playable songs. With no exact match and no playable
     * songs the query is assumed to be a near miss (misheard name) and the closest artist or
     * album result is used, otherwise the Assistant announces playback but nothing plays.
     */
    @JvmStatic
    fun route(query: String, matches: List<MediaItem>): VoiceRoute {
        val exactArtistId = firstId(matches, ConstantsAA.ARTIST_ID, query)
        val exactAlbumId = firstId(matches, ConstantsAA.ALBUM_ID, query)
        val playableSongs = matches.filter { it.mediaMetadata.isPlayable == true }

        val artistId = exactArtistId
            ?: if (exactAlbumId == null && playableSongs.isEmpty()) firstId(matches, ConstantsAA.ARTIST_ID, null) else null
        val albumId = exactAlbumId
            ?: if (artistId == null && playableSongs.isEmpty()) firstId(matches, ConstantsAA.ALBUM_ID, null) else null

        return when {
            artistId != null -> VoiceRoute.Artist(artistId)
            albumId != null -> VoiceRoute.Album(albumId)
            else -> VoiceRoute.Songs(playableSongs)
        }
    }

    private fun firstId(matches: List<MediaItem>, prefix: String, exactTitle: String?): String? =
        matches.firstOrNull {
            it.mediaId.startsWith(prefix) &&
                    (exactTitle == null || it.mediaMetadata.title?.toString().equals(exactTitle, ignoreCase = true))
        }?.mediaId?.removePrefix(prefix)
}
