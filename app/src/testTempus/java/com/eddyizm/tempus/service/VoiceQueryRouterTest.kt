package com.eddyizm.tempus.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.eddyizm.tempus.util.ConstantsAA
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceQueryRouterTest {

    private fun item(id: String, title: String, playable: Boolean = false) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsPlayable(playable).build())
        .build()

    private fun artist(id: String, name: String) = item(ConstantsAA.ARTIST_ID + id, name)
    private fun album(id: String, name: String) = item(ConstantsAA.ALBUM_ID + id, name)
    private fun song(id: String, title: String) = item(id, title, playable = true)

    @Test fun exactArtistMatchWinsOverAlbumsAndSongs() {
        val matches = listOf(album("al1", "Adele"), song("s1", "Adele Song"), artist("ar1", "adele"))
        assertEquals(VoiceRoute.Artist("ar1"), VoiceQueryRouter.route("Adele", matches))
    }

    @Test fun exactAlbumMatchWhenNoExactArtist() {
        val matches = listOf(artist("ar1", "Pink Floyd"), album("al1", "The Wall"), song("s1", "Another Brick"))
        assertEquals(VoiceRoute.Album("al1"), VoiceQueryRouter.route("the wall", matches))
    }

    @Test fun playableSongsWhenNothingMatchesExactly() {
        val s1 = song("s1", "Hey Jude")
        val matches = listOf(artist("ar1", "The Beatles"), album("al1", "1"), s1)
        assertEquals(VoiceRoute.Songs(listOf(s1)), VoiceQueryRouter.route("hey jude live", matches))
    }

    @Test fun nearMissFallsBackToClosestArtist() {
        val matches = listOf(album("al1", "Whiskey on You"), artist("ar1", "Ella Langley"))
        assertEquals(VoiceRoute.Artist("ar1"), VoiceQueryRouter.route("emma langley", matches))
    }

    @Test fun nearMissFallsBackToClosestAlbumWhenNoArtistResult() {
        val matches = listOf(album("al1", "Rumours"))
        assertEquals(VoiceRoute.Album("al1"), VoiceQueryRouter.route("rumors", matches))
    }

    @Test fun noResultsGivesEmptySongs() {
        assertEquals(VoiceRoute.Songs(emptyList()), VoiceQueryRouter.route("nothing here", emptyList()))
    }
}
