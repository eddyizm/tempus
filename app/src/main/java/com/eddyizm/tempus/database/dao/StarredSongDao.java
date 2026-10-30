package com.eddyizm.tempus.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.eddyizm.tempus.model.StarredSong;

import java.util.List;

@Dao
public interface StarredSongDao {
    @Query("SELECT * FROM starred_song ORDER BY starred_date DESC")
    LiveData<List<StarredSong>> getAllLive();

    @Query("SELECT * FROM starred_song ORDER BY starred_date DESC")
    List<StarredSong> getAllSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(StarredSong starredSong);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<StarredSong> starredSongs);

    @Query("DELETE FROM starred_song")
    void deleteAll();

    @Query("SELECT EXISTS(SELECT 1 FROM starred_song WHERE song_id = :songId)")
    boolean exists(String songId);
}
