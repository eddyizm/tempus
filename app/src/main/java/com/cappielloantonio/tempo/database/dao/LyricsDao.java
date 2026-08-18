package com.cappielloantonio.tempo.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.LyricsCache;

@Dao
public interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE song_id = :songId")
    LyricsCache getOne(String songId);

    @Query("SELECT * FROM lyrics_cache WHERE song_id = :songId")
    LiveData<LyricsCache> observeOne(String songId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LyricsCache lyricsCache);

    @Query("DELETE FROM lyrics_cache WHERE song_id = :songId")
    void delete(String songId);
}