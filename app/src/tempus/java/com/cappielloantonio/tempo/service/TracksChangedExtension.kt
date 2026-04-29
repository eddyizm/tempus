package com.cappielloantonio.tempo.service

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import com.cappielloantonio.tempo.repository.AutomotiveRepository
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.Preferences
import com.google.common.util.concurrent.ListenableFuture
import kotlin.text.removePrefix

private const val TAG = "TracksChangedExtension"
@UnstableApi
class TracksChangedExtension(
       private val automotiveRepository: AutomotiveRepository
) : MediaServiceExtension {

    @OptIn(UnstableApi::class)
    override fun handle(
        player: Player,
        item: MediaItem,
        browserFuture: ListenableFuture<MediaBrowser>
    ): Boolean {

        if (player.mediaItemCount > 1) {
            return false
        }

        val extras = item.requestMetadata.extras ?: item.mediaMetadata.extras
        val parentId = extras?.getString("parent_id")

        if (parentId?.startsWith(Constants.AA_INSTANTMIX_SOURCE) == true) {
            Preferences.setLastInstantMix()

            // disconnect handle
            MediaServiceExtensionRegistry.handler = null

            val withoutPrefix = parentId.removePrefix(Constants.AA_INSTANTMIX_SOURCE)
            val countStr = withoutPrefix.substringAfter("[").substringBefore("]")
            val artistId = withoutPrefix.substringAfter("]")
            val count = countStr.toIntOrNull() ?: automotiveRepository.INSTANT_MIX_NUMBER_OF_TRACKS_IN_SMALL_MIX

            Log.d(TAG, "handle: Instant Mix is running for artist $artistId count=$count")

            automotiveRepository.instantMixBuilder.buildAndEnqueue(
                artistId,
                item.mediaId,
                (count-1),
                browserFuture
            )
            return true
        }

        return false
    }
}
