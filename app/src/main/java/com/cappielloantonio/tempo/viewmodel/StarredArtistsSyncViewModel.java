package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class StarredArtistsSyncViewModel extends AndroidViewModel {
    private final ArtistRepository artistRepository;

    private final MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredArtistSongs = new MutableLiveData<>(null);

    public StarredArtistsSyncViewModel(@NonNull Application application) {
        super(application);
        artistRepository = new ArtistRepository();
    }

    public LiveData<List<ArtistID3>> getStarredArtists(LifecycleOwner owner) {
        artistRepository.getStarredArtists(false, -1).observe(owner, starredArtists::postValue);
        return starredArtists;
    }

    public LiveData<List<Child>> getAllStarredArtistSongs() {
        artistRepository.getStarredArtists(false, -1).observeForever(new Observer<List<ArtistID3>>() {
            @Override
            public void onChanged(List<ArtistID3> artists) {
                if (artists != null && !artists.isEmpty()) {
                    collectAllArtistSongs(artists, starredArtistSongs::postValue);
                } else {
                    starredArtistSongs.postValue(new ArrayList<>());
                }
                artistRepository.getStarredArtists(false, -1).removeObserver(this);
            }
        });
        
        return starredArtistSongs;
    }

    public LiveData<List<Child>> getStarredArtistSongs(Activity activity) {
        artistRepository.getStarredArtists(false, -1).observe((LifecycleOwner) activity, artists -> {
            if (artists != null && !artists.isEmpty()) {
                collectAllArtistSongs(artists, starredArtistSongs::postValue);
            } else {
                starredArtistSongs.postValue(new ArrayList<>());
            }
        });
        return starredArtistSongs;
    }

    private void collectAllArtistSongs(List<ArtistID3> artists, ArtistSongsCallback callback) {
        if (artists == null || artists.isEmpty()) {
            callback.onSongsCollected(new ArrayList<>());
            return;
        }

        List<Child> allSongs = new ArrayList<>();
        AtomicInteger remainingArtists = new AtomicInteger(artists.size());
        
        for (ArtistID3 artist : artists) {
            artistRepository.getArtistAllSongs(artist.getId(), new ArtistRepository.ArtistSongsCallback() {
                @Override
                public void onSongsCollected(List<Child> songs) {
                    if (songs != null) {
                        allSongs.addAll(songs);
                    }
                    
                    int remaining = remainingArtists.decrementAndGet();
                    if (remaining == 0) {
                        callback.onSongsCollected(allSongs);
                    }
                }
            });
        }
    }

    private interface ArtistSongsCallback {
        void onSongsCollected(List<Child> songs);
    }
}