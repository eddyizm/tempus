package com.eddyizm.tempus.cast

import android.content.ContentResolver
import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.MusicUtil
import com.eddyizm.tempus.util.Preferences
import com.google.android.gms.cast.MediaQueueItem
import java.util.Locale

/**
 * Cast receivers fetch artwork and streams by URL over the network, so content:// artwork
 * (issue #115) and downloaded tracks' local URIs are rewritten to server URLs. Items also carry
 * the MIME type "audio", which is not valid; receivers need a real one since stream URLs have no
 * extension.
 */
@UnstableApi
class CastMediaItemConverter : MediaItemConverter {

    private val delegate = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem =
        delegate.toMediaQueueItem(withRemoteStream(withRemoteArtwork(mediaItem)))

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem =
        delegate.toMediaItem(mediaQueueItem)

    private fun withRemoteArtwork(item: MediaItem): MediaItem {
        val artwork = item.mediaMetadata.artworkUri ?: return item
        if (artwork.scheme != ContentResolver.SCHEME_CONTENT) return item

        // content://.../albumArt/<id> — the last segment is the cover-art id. Radio covers (rl_/ir_)
        // aren't server cover ids (local file / arbitrary URL), so leave those untouched.
        val coverArtId = artwork.lastPathSegment ?: return item
        if (coverArtId.startsWith("rl_") || coverArtId.startsWith("ir_")) return item

        val httpUrl = Uri.parse(CustomGlideRequest.createUrl(coverArtId, Preferences.getImageSize()))
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(httpUrl).build())
            .build()
    }

    private fun withRemoteStream(item: MediaItem): MediaItem {
        val localConfiguration = item.localConfiguration ?: return item
        val extras = item.mediaMetadata.extras ?: item.requestMetadata.extras
        val isMusic = extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC

        var uri = localConfiguration.uri
        if (isMusic && isLocal(uri) && item.mediaId.isNotEmpty()) {
            uri = MusicUtil.getStreamUri(item.mediaId)
        }

        val mimeType = resolveMimeType(uri, localConfiguration.mimeType, extras)
        val metadata = if (item.mediaMetadata.mediaType == null) {
            item.mediaMetadata.buildUpon().setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()
        } else {
            item.mediaMetadata
        }

        return item.buildUpon()
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun isLocal(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == ContentResolver.SCHEME_FILE

    // A transcode requested in the URL wins, then what the server reported, then MP3.
    private fun resolveMimeType(uri: Uri, current: String?, extras: android.os.Bundle?): String {
        if (current != null && current.contains('/')) return current

        val format = runCatching { uri.getQueryParameter("format") }.getOrNull()
        if (!format.isNullOrEmpty() && !format.equals("raw", ignoreCase = true)) {
            mimeForSuffix(format)?.let { return it }
        }

        extras?.getString("transcodedContentType")?.takeIf { it.contains('/') }?.let { return it }
        extras?.getString("transcodedSuffix")?.let { mimeForSuffix(it) }?.let { return it }
        extras?.getString("contentType")?.takeIf { it.contains('/') }?.let { return it }
        extras?.getString("suffix")?.let { mimeForSuffix(it) }?.let { return it }

        return MimeTypes.AUDIO_MPEG
    }

    private fun mimeForSuffix(suffix: String): String? = when (suffix.lowercase(Locale.ROOT)) {
        "mp3" -> MimeTypes.AUDIO_MPEG
        "aac", "adts" -> MimeTypes.AUDIO_AAC
        "m4a", "mp4", "alac" -> MimeTypes.AUDIO_MP4
        "flac" -> MimeTypes.AUDIO_FLAC
        "ogg", "oga", "vorbis" -> MimeTypes.AUDIO_OGG
        "opus" -> MimeTypes.AUDIO_OGG
        "wav" -> MimeTypes.AUDIO_WAV
        "webm" -> MimeTypes.AUDIO_WEBM
        else -> null
    }
}
