package com.eddyizm.tempus.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eddyizm.tempus.model.PendingPlaylistEdit

@Dao
interface PendingPlaylistEditDao {
    @Query("SELECT * FROM pending_playlist_edit WHERE playlistId = :id AND serverId = :server")
    fun get(id: String, server: String): PendingPlaylistEdit?

    @Query("SELECT * FROM pending_playlist_edit WHERE playlistId = :id AND serverId = :server")
    fun observe(id: String, server: String): LiveData<PendingPlaylistEdit?>

    @Query("SELECT * FROM pending_playlist_edit WHERE serverId = :server")
    fun getAll(server: String): List<PendingPlaylistEdit>

    @Query("SELECT * FROM pending_playlist_edit WHERE serverId = :server AND state = 'PENDING'")
    fun getAllPending(server: String): List<PendingPlaylistEdit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(edit: PendingPlaylistEdit)

    @Query("DELETE FROM pending_playlist_edit WHERE playlistId = :id AND serverId = :server")
    fun remove(id: String, server: String)
}
