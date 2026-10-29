package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.eddyizm.tempus.repository.DownloadRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.model.Download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LikedViewModel extends AndroidViewModel {
    private static final String TAG = "LikedViewModel";

    private final SongRepository songRepository;
    private final DownloadRepository downloadRepository;

    private final MutableLiveData<List<Child>> starredSongs = new MutableLiveData<>(new ArrayList<>());
    private final LiveData<List<Download>> liveDownloads;

    private final MediatorLiveData<List<Child>> combinedSongs = new MediatorLiveData<>();

    public LikedViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        downloadRepository = new DownloadRepository();
        liveDownloads = downloadRepository.getLiveDownload();

        // Combine starred songs + downloads
        combinedSongs.addSource(starredSongs, this::mergeSongs);
        combinedSongs.addSource(liveDownloads, this::mergeSongs);

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

        Log.d(TAG, "Merged liked songs: " + result.size() + " total (" +
                (starred != null ? starred.size() : 0) + " starred + " +
                (downloads != null ? downloads.size() : 0) + " downloaded)");
        combinedSongs.setValue(result);
    }
}
