package com.cappielloantonio.tempo.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.InternetRadioStationCache;

import java.util.List;

@Dao
public interface InternetRadioStationDao {
    @Query("SELECT * FROM internet_radio_station_cache")
    List<InternetRadioStationCache> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<InternetRadioStationCache> stations);

    @Query("DELETE FROM internet_radio_station_cache")
    void deleteAll();
}
