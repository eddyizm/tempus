package com.eddyizm.tempus.cast

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CastMimeTypeTest {

    private fun extras(
        suffix: String? = null,
        contentType: String? = null,
        path: String? = null,
        transcodedSuffix: String? = null,
        transcodedContentType: String? = null,
    ): Bundle = mock {
        on { getString("suffix") } doReturn suffix
        on { getString("contentType") } doReturn contentType
        on { getString("path") } doReturn path
        on { getString("transcodedSuffix") } doReturn transcodedSuffix
        on { getString("transcodedContentType") } doReturn transcodedContentType
    }

    @Test
    fun aTranscodeAskedForInTheUrlWins() {
        val flac = extras(suffix = "flac", path = "Artist/Album/01 track.flac", transcodedSuffix = "mp3")
        assertEquals("audio/mpeg", CastMimeType.forStream("mp3", flac))
        assertEquals("audio/ogg", CastMimeType.forStream("opus", flac))
        assertEquals("audio/aac", CastMimeType.forStream("aac", flac))
    }

    // An unknown transcode target is still not the original file.
    @Test
    fun anUnknownTranscodeLeavesTheTypeUnset() {
        assertNull(CastMimeType.forStream("wma", extras(suffix = "flac")))
    }

    // Review: with format=raw the server's transcode fields describe a stream that is not sent.
    @Test
    fun rawStreamsTheOriginalFileEvenWhenTheServerFillsTranscodeFields() {
        val flac = extras(
            suffix = "flac",
            contentType = "audio/flac",
            path = "Artist/Album/01 track.flac",
            transcodedSuffix = "mp3",
            transcodedContentType = "audio/mpeg",
        )
        assertEquals("audio/flac", CastMimeType.forStream("raw", flac))
    }

    // Review: an opus download of a FLAC file has its suffix rewritten, the path keeps flac.
    @Test
    fun aTranscodedDownloadStreamsAsTheServerFile() {
        val download = extras(suffix = "opus", contentType = "audio/opus", path = "Artist/Album/01 track.flac")
        assertEquals("audio/flac", CastMimeType.forStream("raw", download))
    }

    @Test
    fun withoutAFormatTheServerPicksAndItsTranscodeFieldsApply() {
        val flac = extras(suffix = "flac", path = "a/b.flac", transcodedSuffix = "mp3")
        assertEquals("audio/mpeg", CastMimeType.forStream(null, flac))
        assertEquals("audio/flac", CastMimeType.forStream(null, extras(suffix = "flac", path = "a/b.flac")))
    }

    // Servers report types like audio/x-flac; the suffix maps to one Cast lists.
    @Test
    fun theSuffixBeatsTheServersContentType() {
        assertEquals("audio/flac", CastMimeType.forStream("raw", extras(suffix = "flac", contentType = "audio/x-flac")))
        assertEquals("audio/mp4", CastMimeType.forStream("raw", extras(suffix = "m4a", contentType = "audio/x-m4a")))
    }

    @Test
    fun theContentTypeIsTheLastResort() {
        assertEquals("audio/x-ms-wma", CastMimeType.forStream("raw", extras(suffix = "wma", contentType = "audio/x-ms-wma")))
        assertNull(CastMimeType.forStream("raw", extras(suffix = "mkv", contentType = "video/x-matroska")))
        assertNull(CastMimeType.forStream("raw", null))
    }

    // Review: radio used to go out as audio/mpeg whatever it was.
    @Test
    fun radioOnlyGetsATypeFromTheUrlExtension() {
        assertEquals("application/x-mpegurl", CastMimeType.forRadio("playlist.m3u8"))
        assertEquals("audio/aac", CastMimeType.forRadio("live.AAC"))
        assertEquals("audio/mpeg", CastMimeType.forRadio("stream.mp3"))
        assertEquals("audio/ogg", CastMimeType.forRadio("stream.ogg"))
        assertNull(CastMimeType.forRadio("stream"))
        assertNull(CastMimeType.forRadio("listen.pls"))
        assertNull(CastMimeType.forRadio(null))
    }

    // Review: one table, MusicUtil's, with opus as the one Cast difference.
    @Test
    fun suffixesUseMusicUtilsTableExceptOpus() {
        assertEquals("audio/ogg", CastMimeType.forSuffix("opus"))
        assertEquals("audio/aac", CastMimeType.forSuffix("aac"))
        assertEquals("audio/mpeg", CastMimeType.forSuffix("mp3"))
        assertEquals("audio/flac", CastMimeType.forSuffix("FLAC"))
        assertEquals("audio/mp4", CastMimeType.forSuffix("m4a"))
        assertEquals("audio/ogg", CastMimeType.forSuffix("oga"))
        assertEquals("audio/wav", CastMimeType.forSuffix("wav"))
        assertEquals("audio/webm", CastMimeType.forSuffix("webm"))
        assertNull(CastMimeType.forSuffix(""))
        assertNull(CastMimeType.forSuffix(null))
    }
}
