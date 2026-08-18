package com.eddyizm.tempus.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.eddyizm.tempus.model.RecentSearch;

import java.util.List;

@Dao
public interface RecentSearchDao {
    @Query("SELECT search FROM recent_search ORDER BY timestamp DESC")
    List<String> getRecent();

    @Query("SELECT search FROM recent_search ORDER BY search DESC")
    List<String> getAlpha();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecentSearch search);

    @Delete
    void delete(RecentSearch search);
}