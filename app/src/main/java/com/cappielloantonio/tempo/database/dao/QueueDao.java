package com.cappielloantonio.tempo.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.model.Queue;

import java.util.List;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

@Dao
public interface QueueDao {
    @Query("SELECT * FROM queue")
    Maybe<List<Queue>> getAll();

    @Query("SELECT * FROM queue")
    LiveData<List<Queue>> getAllLive();

    @Query("SELECT * FROM queue")
    List<Queue> getAllSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Queue songQueueObject);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Queue> songQueueObjects);

    @Query("DELETE FROM queue WHERE queue.track_order=:position")
    void delete(int position);

    @Query("DELETE FROM queue")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM queue")
    Single<Integer> count();

    @Query("UPDATE queue SET last_play=:timestamp WHERE id=:id")
    void setLastPlay(String id, long timestamp);

    @Query("UPDATE queue SET playing_changed=:timestamp WHERE id=:id")
    void setPlayingChanged(String id, long timestamp);

    @Query("SELECT * FROM queue ORDER BY last_play DESC LIMIT 1")
    Maybe<Queue> getLastPlayed();
}