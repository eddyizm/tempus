package com.cappielloantonio.tempo.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.cappielloantonio.tempo.model.InternetRadioStationCache;

import java.util.List;

@Dao
public interface InternetRadioStationDao {
    @Query("SELECT * FROM internet_radio_station_cache")
    List<InternetRadioStationCache> getAll();

    @Query("SELECT * FROM internet_radio_station_cache WHERE source = 'local'")
    List<InternetRadioStationCache> getLocal();

    @Query("SELECT * FROM internet_radio_station_cache WHERE id = :id LIMIT 1")
    InternetRadioStationCache getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<InternetRadioStationCache> stations);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(InternetRadioStationCache station);

    @Update
    void update(InternetRadioStationCache station);

    @Query("DELETE FROM internet_radio_station_cache WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM internet_radio_station_cache WHERE source = 'subsonic'")
    void deleteSubsonic();

    @Query("DELETE FROM internet_radio_station_cache")
    void deleteAll();
}
