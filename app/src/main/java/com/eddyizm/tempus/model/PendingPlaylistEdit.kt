package com.eddyizm.tempus.model

import androidx.room.Entity

/**
 * Playlist changes made on the phone while the server could not be reached, waiting to be merged
 * onto the server's list. baseIds is the server's list the edits started from, localIds the
 * phone's list. Keyed by server as well, playlist ids repeat across servers.
 */
@Entity(tableName = "pending_playlist_edit", primaryKeys = ["playlistId", "serverId"])
data class PendingPlaylistEdit(
    val playlistId: String,
    val serverId: String,
    val baseIds: List<String>,
    val localIds: List<String>,
    val newName: String?,
    val updatedAt: Long,
    val state: String,
    val lastError: String?
) {
    companion object {
        const val PENDING = "PENDING"
        const val FAILED = "FAILED"
    }
}
