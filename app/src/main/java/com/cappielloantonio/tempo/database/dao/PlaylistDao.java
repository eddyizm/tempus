package com.cappielloantonio.tempo.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.subsonic.models.Playlist;

import java.util.List;

@Dao
public interface PlaylistDao {

    @Query("SELECT * FROM playlist")
    LiveData<List<Playlist>> getAll();

    @Query("SELECT * FROM playlist")
    List<Playlist> getAllSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Playlist playlist);

    @Delete
    void delete(Playlist playlist);

    @Query("UPDATE playlist SET name = :newName WHERE id = :playlistId")
    void updateName(String playlistId, String newName);
}