package com.cappielloantonio.tempo.cast

import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.util.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class CastArtworkConverterTest {

    // createUrl() (used by the fix) builds a cover-art URL from the active Subsonic session; with no
    // session its auth is null and it NPEs. Casting only ever happens while signed in, so seed a fake
    // login to make this converter test hermetic on a clean device / CI (issue #115).
    @Before
    fun seedFakeSession() {
        Preferences.setServer("https://example.org")
        Preferences.setUser("tester")
        Preferences.setPassword("pw")
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

    /**
     * Issue #115: the default converter hands the in-process content:// artwork URI straight to
     * the Cast receiver. A Chromecast is a separate device and cannot fetch a content:// URI, so
     * no album art shows while casting. This test documents that broken state; the fix swaps in a
     * converter that rewrites artwork to an http(s) cover-art URL, after which the asserted scheme
     * becomes "https".
     */
    @Test
    fun defaultConverter_emitsContentUri_thatCastCannotLoad() {
        val queueItem = DefaultMediaItemConverter().toMediaQueueItem(albumItem())
        val images = queueItem.media!!.metadata!!.images
        assertFalse("artwork should reach the converter", images.isEmpty())
        assertEquals("content", images[0].url.scheme)
    }

    /** The fix: our converter rewrites the content:// artwork to the server cover-art URL. */
    @Test
    fun castConverter_rewritesArtworkToCoverArtUrl() {
        val queueItem = CastMediaItemConverter().toMediaQueueItem(albumItem())
        val url = queueItem.media!!.metadata!!.images[0].url.toString()
        assertNotEquals("content", queueItem.media!!.metadata!!.images[0].url.scheme)
        assertTrue("expected a getCoverArt url, was: $url", url.contains("getCoverArt"))
        assertTrue("expected the cover-art id to be carried over, was: $url", url.contains("al-123"))
    }
}
