package com.cappielloantonio.tempo.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import com.google.common.util.concurrent.ListenableFuture

interface MediaServiceExtension {
    fun handle(
        player: Player,
        item: MediaItem,
        browserFuture: ListenableFuture<MediaBrowser>
    ): Boolean
}

object MediaServiceExtensionRegistry {
    var handler: MediaServiceExtension? = null
}