package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.os.*;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.model.StarredSong;
import com.eddyizm.tempus.repository.DownloadRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.Child;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LikedViewModel extends AndroidViewModel {
    private static final String TAG = "LikedViewModel";

    private final SongRepository songRepository;
    private final DownloadRepository downloadRepository;

    private final MutableLiveData<List<Child>> starredSongs = new MutableLiveData<>(new ArrayList<>());
    private final LiveData<List<Download>> liveDownloads;
    private final LiveData<List<StarredSong>> liveLocalStarredSongs;

    private final MediatorLiveData<List<Child>> combinedSongs = new MediatorLiveData<>();

    private static final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    public LikedViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        downloadRepository = new DownloadRepository();
        liveDownloads = downloadRepository.getLiveDownload();
        liveLocalStarredSongs = AppDatabase.getInstance().starredSongDao().getAllLive();

        // Combine starred songs + downloads + local starred
        combinedSongs.addSource(starredSongs, this::mergeSongs);
        combinedSongs.addSource(liveDownloads, this::mergeSongs);
        combinedSongs.addSource(liveLocalStarredSongs, this::mergeSongs);

        // Fetch starred songs from server
        fetchStarredSongs();
    }

    public LiveData<List<Child>> getLikedSongs() {
        return combinedSongs;
    }

    public LiveData<List<Child>> getStarredSongs() {
        return starredSongs;
    }

    private void fetchStarredSongs() {
        MutableLiveData<List<Child>> starred = songRepository.getStarredSongs(false, -1);
        starred.observeForever(songs -> {
            if (songs != null && !songs.isEmpty()) {
                starredSongs.setValue(songs);
            } else {
                starredSongs.setValue(new ArrayList<>());
            }
        });
    }

    private void mergeSongs(@Nullable Object source) {
        List<Child> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        // Query database on background thread to avoid blocking the main thread
        databaseExecutor.execute(() -> {
            // Build a map of songId -> starredDate from local starred songs
            Map<String, Long> localStarredDates = new HashMap<>();
            List<StarredSong> localStarred = AppDatabase.getInstance().starredSongDao().getAllSync();
            if (localStarred != null) {
                for (StarredSong ss : localStarred) {
                    localStarredDates.put(ss.getSongId(), ss.getStarredDate());
                }
            }

            // Continue merging on main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                // Build a map of songId -> starredDate from local starred songs
                // (already done in background, reuse localStarredDates)

                // Add starred songs first (sorted by starred date, newest first)
                List<Child> starred = starredSongs.getValue();
                if (starred != null) {
                    for (Child song : starred) {
                        if (song.getStarred() != null) {
                            result.add(song);
                            seenIds.add(song.getId());
                        }
                    }
                }

                // Add downloaded songs that are not already in starred
                List<Download> downloads = downloadRepository.getAllDownloads();
                if (downloads != null) {
                    for (Download download : downloads) {
                        if (download.getDownloadState() == 1 && !seenIds.contains(download.getId())) {
                            // Convert Download to Child and add
                            Child child = (Child) download;
                            result.add(child);
                            seenIds.add(child.getId());
                        }
                    }
                }

                // If no starred songs from server (offline), use local starred to order the result
                if ((starred == null || starred.isEmpty()) && !localStarredDates.isEmpty()) {
                    // Sort result by local starred date (newest first)
                    result.sort((a, b) -> {
                        Long dateA = localStarredDates.get(a.getId());
                        Long dateB = localStarredDates.get(b.getId());
                        if (dateA != null && dateB != null) {
                            return Long.compare(dateB, dateA); // newest first
                        } else if (dateA != null) {
                            return -1;
                        } else if (dateB != null) {
                            return 1;
                        } else {
                            return 0;
                        }
                    });
                }

                Log.d(TAG, "Merged liked songs: " + result.size() + " total (" +
                        (starred != null ? starred.size() : 0) + " starred + " +
                        (downloads != null ? downloads.size() : 0) + " downloaded)");
                combinedSongs.setValue(result);
            });
        });
    }
}