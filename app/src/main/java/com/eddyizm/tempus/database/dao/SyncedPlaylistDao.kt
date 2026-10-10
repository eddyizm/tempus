package com.eddyizm.tempus.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import com.eddyizm.tempus.subsonic.models.Playlist

@Dao
interface SyncedPlaylistDao {
    @Query("INSERT OR IGNORE INTO synced_playlist (playlistId, serverId) VALUES (:id, :server)")
    fun add(id: String, server: String)

    @Query("DELETE FROM synced_playlist WHERE playlistId = :id AND serverId = :server")
    fun remove(id: String, server: String)

    @Query("SELECT EXISTS(SELECT 1 FROM synced_playlist WHERE playlistId = :id AND serverId = :server)")
    fun isSynced(id: String, server: String): LiveData<Boolean>

    @Query("SELECT p.* FROM playlist p JOIN synced_playlist s ON p.id = s.playlistId WHERE s.serverId = :server")
    fun getAllSynced(server: String): List<Playlist>
}
