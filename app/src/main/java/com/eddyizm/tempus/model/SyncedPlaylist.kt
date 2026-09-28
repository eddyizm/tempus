package com.eddyizm.tempus.model

import androidx.room.Entity

// Keyed by server as well, playlist ids repeat across servers.
@Entity(tableName = "synced_playlist", primaryKeys = ["playlistId", "serverId"])
data class SyncedPlaylist(
    val playlistId: String,
    val serverId: String
)
