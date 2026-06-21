package com.cappielloantonio.tempo.cast

import android.content.ContentResolver
import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.cappielloantonio.tempo.glide.CustomGlideRequest
import com.cappielloantonio.tempo.util.Preferences
import com.google.android.gms.cast.MediaQueueItem

/**
 * Cast receivers fetch artwork by URL over the network, but the app's media items carry a
 * content:// artwork URI served in-process by AlbumArtContentProvider, which a separate Chromecast
 * device cannot reach (issue #115). Rewrite that to the server's http(s) cover-art URL before the
 * default conversion, so the receiver can load it.
 */
@UnstableApi
class CastMediaItemConverter : MediaItemConverter {

    private val delegate = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem =
        delegate.toMediaQueueItem(withRemoteArtwork(mediaItem))

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
}
