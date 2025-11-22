package com.cappielloantonio.tempo.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.Favorite;

import java.util.List;

import io.reactivex.rxjava3.core.Maybe;

@Dao
public interface FavoriteDao {
    @Query("SELECT * FROM favorite")
    Maybe<List<Favorite>> getAll();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(Favorite favorite);

    @Delete
    void delete(Favorite favorite);

    @Query("DELETE FROM favorite")
    void deleteAll();
}