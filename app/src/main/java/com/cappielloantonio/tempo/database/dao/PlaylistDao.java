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

    // Added to support background caching of the full server list
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Playlist> playlists);

    @Delete
    void delete(Playlist playlist);

    @Query("DELETE FROM playlist") void deleteAll();

    @Query("UPDATE playlist SET name = :newName WHERE id = :playlistId")
    void updateName(String playlistId, String newName);

    @Query("SELECT p.*, (pp.playlistId IS NOT NULL) AS isPinned " +
       "FROM playlist p " +
       "LEFT JOIN pinned_playlist pp ON p.id = pp.playlistId " +
       "ORDER BY " +
       "CASE WHEN :sortMethod LIKE 'ORDER_BY_RANDOM' THEN RANDOM() END ASC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_PINNED' THEN pp.playlistId IS NOT NULL END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_NAME' THEN p.name END ASC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_DATE' THEN p.created END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_SONGS' THEN p.songCount END DESC")
LiveData<List<Playlist>> getSortedPlaylists(String sortMethod);
}