package com.cappielloantonio.tempo.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.PlaylistSong;

import java.util.List;

@Dao
public interface PlaylistSongDao {
    @Query("SELECT * FROM playlist_song WHERE playlist_id = :playlistId")
    LiveData<List<PlaylistSong>> getSongsForPlaylist(String playlistId);

    @Query("SELECT * FROM playlist_song WHERE playlist_id = :playlistId")
    List<PlaylistSong> getSongsForPlaylistSync(String playlistId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PlaylistSong> playlistSongs);

    @Query("DELETE FROM playlist_song WHERE playlist_id = :playlistId")
    void deleteForPlaylist(String playlistId);

    @Query("DELETE FROM playlist_song")
    void deleteAll();
}
