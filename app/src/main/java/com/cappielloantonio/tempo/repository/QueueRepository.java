package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.QueueDao;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QueueRepository {
    private static final String TAG = "QueueRepository";

    private final QueueDao queueDao = AppDatabase.getInstance().queueDao();

    public Maybe<List<Queue>> getQueue() {
        return queueDao.getAll();
    }

    public Maybe<List<Child>> getMedia() {
        return queueDao.getAll().map(queue -> {
            if (queue == null)
                return null;
            return queue.stream()
                    .map(Child.class::cast)
                    .collect(Collectors.toList());
        });
    }

    public LiveData<List<Child>> getMediaLive() {
        return Transformations.map(queueDao.getAllLive(), queue -> {
            if (queue == null)
                return null;
            return queue.stream()
                    .map(Child.class::cast)
                    .collect(Collectors.toList());
        });
    }


    public MutableLiveData<PlayQueue> getPlayQueue() {
        MutableLiveData<PlayQueue> playQueue = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .getPlayQueue()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlayQueue() != null) {
                            playQueue.setValue(response.body().getSubsonicResponse().getPlayQueue());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        playQueue.setValue(null);
                    }
                });

        return playQueue;
    }

    public void savePlayQueue(List<String> ids, String current, long position) {
        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .savePlayQueue(ids, current, position)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void insert(Child media, boolean reset, int afterIndex) {
        App.getExecutor().submit(() -> {
            List<Queue> mediaList = reset ? new ArrayList<>() : queueDao.getAllSync();
            Queue queueItem = new Queue(media);
            mediaList.add(afterIndex, queueItem);
            for (int i = 0; i < mediaList.size(); i++) {
                mediaList.get(i).setTrackOrder(i);
            }
            queueDao.deleteAll();
            queueDao.insertAll(mediaList);
        });
    }

    private boolean isMediaInQueue(List<Queue> queue, Child media) {
        if (queue == null || media == null) return false;
        
        return queue.stream().anyMatch(queueItem -> 
            queueItem != null && media.getId() != null && 
            queueItem.getId().equals(media.getId())
        );
    }

    public void insertAll(List<Child> toAdd, boolean reset, int afterIndex) {
        App.getExecutor().submit(() -> {
            List<Queue> media = reset ? new ArrayList<>() : queueDao.getAllSync();
            for (int i = 0; i < toAdd.size(); i++) {
                Queue queueItem = new Queue(toAdd.get(i));
                media.add(afterIndex + i, queueItem);
            }
            for (int i = 0; i < media.size(); i++) {
                media.get(i).setTrackOrder(i);
            }
            queueDao.deleteAll();
            queueDao.insertAll(media);
        });
    }

    public void delete(int position) {
        App.getExecutor().submit(() -> queueDao.delete(position));
    }

    public void deleteAll() {
        App.getExecutor().submit(queueDao::deleteAll);
    }

    public Single<Integer> count() {
        return queueDao.count();
    }

    public void setLastPlayedTimestamp(String id) {
        App.getExecutor().submit(() -> queueDao.setLastPlay(id, System.currentTimeMillis()));
    }

    public void setPlayingPausedTimestamp(String id, long ms) {
        App.getExecutor().submit(() -> queueDao.setPlayingChanged(id, ms));
    }

    public Maybe<Integer> getLastPlayedMediaIndex() {
        return queueDao.getLastPlayed().map(q -> q == null ? 0 : q.getTrackOrder());
    }

    public Maybe<Long> getLastPlayedMediaTimestamp() {
        return queueDao.getLastPlayed().map(q -> q == null ? 0 : q.getPlayingChanged());
    }
}
