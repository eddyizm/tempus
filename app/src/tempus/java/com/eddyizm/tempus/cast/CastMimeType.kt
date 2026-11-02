package com.eddyizm.tempus.cast

import android.os.Bundle
import androidx.media3.common.MimeTypes
import com.eddyizm.tempus.util.MusicUtil
import java.util.Locale

/**
 * The type a Cast receiver is told for what it fetches. The receiver always streams from the
 * server, so the type follows the stream URL: the format it asks the server to transcode to, else
 * the file on the server. A downloaded copy's format says nothing about that stream. Null when
 * nothing is known, so the receiver works it out from the stream rather than trust a guess.
 */
internal object CastMimeType {

    // Lower case, as Cast's own examples write it. MIME types are case-insensitive anyway.
    private const val HLS = "application/x-mpegurl"

    /** [format] is the stream URL's format parameter, null when the URL has none and the server picks. */
    fun forStream(format: String?, extras: Bundle?): String? {
        if (!format.isNullOrEmpty() && !format.equals("raw", ignoreCase = true)) {
            // Still a transcode when the format is not one we know, so the source type would be wrong.
            return forSuffix(format)
        }

        if (format == null) {
            // The server decides, so what it says it transcodes to for this client is the best guess.
            forSuffix(extras?.getString("transcodedSuffix"))?.let { return it }
            audioType(extras?.getString("transcodedContentType"))?.let { return it }
        }

        // The original file. A transcoded download rewrites the suffix and content type to the
        // download's format; the path keeps the server file's extension.
        forSuffix(MusicUtil.sourceSuffix(extras))?.let { return it }
        return audioType(extras?.getString("contentType"))
    }

    /**
     * Stations carry no format, so only the URL's extension says anything. A wrong type sends the
     * receiver down the wrong path (HLS as a plain file fails), so anything else stays unset.
     */
    fun forRadio(lastPathSegment: String?): String? {
        val extension = lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
        if (extension.isNullOrEmpty()) return null
        if (extension == "m3u8") return HLS
        return forSuffix(extension)
    }

    fun forSuffix(suffix: String?): String? {
        if (suffix.isNullOrEmpty()) return null
        // MusicUtil names opus after its codec. A receiver loads by container, the server streams
        // opus in Ogg, and audio/opus is not a type Cast lists.
        if (suffix.equals("opus", ignoreCase = true)) return MimeTypes.AUDIO_OGG
        return MusicUtil.audioMimeTypeForFormat(suffix)
    }

    private fun audioType(contentType: String?): String? =
        contentType?.takeIf { it.startsWith("audio/", ignoreCase = true) }
}
