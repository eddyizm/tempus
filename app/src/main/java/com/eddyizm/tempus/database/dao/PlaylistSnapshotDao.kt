package com.eddyizm.tempus.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eddyizm.tempus.model.PlaylistSnapshot

@Dao
interface PlaylistSnapshotDao {
    @Query("SELECT * FROM playlist_snapshot WHERE playlistId = :id AND serverId = :server")
    fun get(id: String, server: String): PlaylistSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(snapshot: PlaylistSnapshot)

    @Query("DELETE FROM playlist_snapshot WHERE playlistId = :id AND serverId = :server")
    fun remove(id: String, server: String)
}
