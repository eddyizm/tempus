package com.eddyizm.tempus.cast

import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eddyizm.tempus.App
import com.eddyizm.tempus.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class CastArtworkConverterTest {

    private var savedServer: String? = null
    private var savedUser: String? = null
    private var savedPassword: String? = null

    // createUrl() NPEs with no active session (its auth is null), and casting only happens while
    // signed in, so seed a fake login to keep this hermetic on a clean device / CI (issue #115).
    @Before
    fun seedFakeSession() {
        savedServer = Preferences.getServer()
        savedUser = Preferences.getUser()
        savedPassword = Preferences.getPassword()
        Preferences.setServer("https://example.org")
        Preferences.setUser("tester")
        Preferences.setPassword("pw")
        App.getSubsonicClientInstance(true)
    }

    // The seeded login is global state; without this every later test in the run inherits it.
    @After
    fun restoreSession() {
        Preferences.setServer(savedServer)
        Preferences.setUser(savedUser)
        Preferences.setPassword(savedPassword)
        App.getSubsonicClientInstance(true)
    }

    // Mirrors how the app builds media items: artwork is a content:// URI served in-process by
    // AlbumArtContentProvider (see MappingUtil / SessionMediaItem).
    private fun albumItem(): MediaItem {
        val artworkUri = Uri.parse(
            "content://com.eddyizm.tempus.debug.albumart.provider/albumArt/al-123"
        )
        return MediaItem.Builder()
            .setUri("https://example.org/rest/stream?id=42")
            .setMimeType(MimeTypes.AUDIO_MPEG)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Song")
                    .setArtist("Artist")
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()
    }

    /** Documents the broken state from issue #115; see CastMediaItemConverter for why it breaks. */
    @Test
    fun defaultConverter_emitsContentUri_thatCastCannotLoad() {
        val queueItem = DefaultMediaItemConverter().toMediaQueueItem(albumItem())
        val images = queueItem.media!!.metadata!!.images
        assertFalse("artwork should reach the converter", images.isEmpty())
        assertEquals("content", images[0].url.scheme)
    }

    @Test
    fun castConverter_rewritesArtworkToCoverArtUrl() {
        val queueItem = CastMediaItemConverter().toMediaQueueItem(albumItem())
        val artwork = queueItem.media!!.metadata!!.images[0].url
        val url = artwork.toString()
        // Either scheme: the URL comes from the in-use server address, which is the local one when set.
        assertTrue("expected a scheme the receiver can fetch, was: $url", artwork.scheme == "http" || artwork.scheme == "https")
        assertTrue("expected a getCoverArt url, was: $url", url.contains("getCoverArt"))
        assertTrue("expected the cover-art id to be carried over, was: $url", url.contains("al-123"))
    }
}
