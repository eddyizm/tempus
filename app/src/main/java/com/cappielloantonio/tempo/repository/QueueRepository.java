package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.QueueDao;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QueueRepository {
    private static final String TAG = "QueueRepository";
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private final QueueDao queueDao = AppDatabase.getInstance().queueDao();

    public LiveData<List<Queue>> getLiveQueue() {
        return queueDao.getAll();
    }

    public List<Child> getMedia() {
        List<Child> media = new ArrayList<>();

        GetMediaThreadSafe getMedia = new GetMediaThreadSafe(queueDao);
        Thread thread = new Thread(getMedia);
        thread.start();

        try {
            thread.join();
            media = getMedia.getMedia().stream()
                    .map(Child.class::cast)
                    .collect(Collectors.toList());

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return media;
    }

    public MutableLiveData<PlayQueue> getPlayQueue() {
        MutableLiveData<PlayQueue> playQueue = new MutableLiveData<>();

        Log.d(TAG, "Getting play queue from server...");

        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .getPlayQueue()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlayQueue() != null) {
                            PlayQueue serverQueue = response.body().getSubsonicResponse().getPlayQueue();
                            Log.d(TAG, "Server returned play queue with " +
                                    (serverQueue.getEntries() != null ? serverQueue.getEntries().size() : 0) + " items");
                            playQueue.setValue(serverQueue);
                        } else {
                            Log.d(TAG, "Server returned no play queue");
                            playQueue.setValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Failed to get play queue", t);
                        playQueue.setValue(null);
                    }
                });

        return playQueue;
    }

    public void savePlayQueue(List<String> ids, String current, long position) {
        Log.d(TAG, "Saving play queue to server - Items: " + ids.size() + ", Current: " + current);

        App.getSubsonicClientInstance(false)
                .getBookmarksClient()
                .savePlayQueue(ids, current, position)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Play queue saved successfully");
                        } else {
                            Log.d(TAG, "Play queue save failed with code: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Play queue save failed", t);
                    }
                });
    }

    public void insert(Child media, boolean reset, int afterIndex) {
        dbExecutor.execute(() -> {
            List<Queue> mediaList = new ArrayList<>();

            if (!reset) {
                mediaList = queueDao.getAllSimple();
            }

            Queue queueItem = new Queue(media);
            mediaList.add(afterIndex, queueItem);

            for (int i = 0; i < mediaList.size(); i++) {
                mediaList.get(i).setTrackOrder(i);
            }

            queueDao.replaceQueue(mediaList);
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
        dbExecutor.execute(() -> {
            List<Queue> media = new ArrayList<>();

            if (!reset) {
                media = queueDao.getAllSimple();
            }

            final List<Queue> finalMedia = media;
            List<Child> filteredToAdd = toAdd.stream()
                    .filter(child -> !isMediaInQueue(finalMedia, child))
                    .collect(Collectors.toList());

            for (int i = 0; i < filteredToAdd.size(); i++) {
                Queue queueItem = new Queue(filteredToAdd.get(i));
                media.add(afterIndex + i, queueItem);
            }

            for (int i = 0; i < media.size(); i++) {
                media.get(i).setTrackOrder(i);
            }

            queueDao.replaceQueue(media);
        });
    }

    public void delete(int position) {
        dbExecutor.execute(() -> queueDao.delete(position));
    }

    public void deleteAll() {
        dbExecutor.execute(queueDao::deleteAll);
    }

    public int count() {
        int count = 0;

        CountThreadSafe countThread = new CountThreadSafe(queueDao);
        Thread thread = new Thread(countThread);
        thread.start();

        try {
            thread.join();
            count = countThread.getCount();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return count;
    }

    public void setLastPlayedTimestamp(String id) {
        dbExecutor.execute(() -> queueDao.setLastPlay(id, System.currentTimeMillis()));
    }

    public void setPlayingPausedTimestamp(String id, long ms) {
        dbExecutor.execute(() -> queueDao.setPlayingChanged(id, ms));
    }

    public int getLastPlayedMediaIndex() {
        int index = 0;

        GetLastPlayedMediaThreadSafe getLastPlayedMediaThreadSafe = new GetLastPlayedMediaThreadSafe(queueDao);
        Thread thread = new Thread(getLastPlayedMediaThreadSafe);
        thread.start();

        try {
            thread.join();
            Queue lastMediaPlayed = getLastPlayedMediaThreadSafe.getQueueItem();
            if (lastMediaPlayed != null) {
                index = lastMediaPlayed.getTrackOrder();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return index;
    }

    public long getLastPlayedMediaTimestamp() {
        long timestamp = 0;

        GetLastPlayedMediaThreadSafe getLastPlayedMediaThreadSafe = new GetLastPlayedMediaThreadSafe(queueDao);
        Thread thread = new Thread(getLastPlayedMediaThreadSafe);
        thread.start();

        try {
            thread.join();
            Queue lastMediaPlayed = getLastPlayedMediaThreadSafe.getQueueItem();
            if (lastMediaPlayed != null) {
                timestamp = lastMediaPlayed.getPlayingChanged();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return timestamp;
    }

    private static class GetMediaThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private List<Queue> media;

        public GetMediaThreadSafe(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public void run() {
            media = queueDao.getAllSimple();
        }

        public List<Queue> getMedia() {
            return media;
        }
    }

    private static class CountThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private int count = 0;

        public CountThreadSafe(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public void run() {
            count = queueDao.count();
        }

        public int getCount() {
            return count;
        }
    }

    private static class GetLastPlayedMediaThreadSafe implements Runnable {
        private final QueueDao queueDao;
        private Queue lastMediaPlayed;

        public GetLastPlayedMediaThreadSafe(QueueDao queueDao) {
            this.queueDao = queueDao;
        }

        @Override
        public void run() {
            lastMediaPlayed = queueDao.getLastPlayed();
        }

        public Queue getQueueItem() {
            return lastMediaPlayed;
        }
    }
}