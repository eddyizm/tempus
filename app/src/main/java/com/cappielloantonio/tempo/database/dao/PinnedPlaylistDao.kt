package com.cappielloantonio.tempo.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cappielloantonio.tempo.model.PinnedPlaylist

@Dao
interface PinnedPlaylistDao {
    @Query("INSERT OR IGNORE INTO pinned_playlist (playlistId) VALUES (:id)")
    fun pin(id: String)

    @Query("DELETE FROM pinned_playlist WHERE playlistId = :id")
    fun unpin(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_playlist WHERE playlistId = :id)")
    fun isPinned(id: String): Boolean

    @Query("SELECT playlistId FROM pinned_playlist")
    fun getAllPinnedIds(): LiveData<List<PinnedPlaylist>>
}                                                       