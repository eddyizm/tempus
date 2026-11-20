package com.eddyizm.tempus.model

import androidx.room.Entity

// The track ids of a playlist in the order the server last returned them, duplicates included.
@Entity(tableName = "playlist_snapshot", primaryKeys = ["playlistId", "serverId"])
data class PlaylistSnapshot(
    val playlistId: String,
    val serverId: String,
    val songIds: List<String>
)
