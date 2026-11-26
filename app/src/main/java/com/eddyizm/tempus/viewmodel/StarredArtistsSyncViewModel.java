package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StarredArtistsSyncViewModel extends AndroidViewModel {
    private final ArtistRepository artistRepository;

    private final MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(null);
    private final MutableLiveData<StarredArtistSongs> starredArtistSongs = new MutableLiveData<>(null);
    private final AtomicBoolean checkInFlight = new AtomicBoolean(false);
    private final AtomicBoolean rerunWanted = new AtomicBoolean(false);

    public StarredArtistsSyncViewModel(@NonNull Application application) {
        super(application);
        artistRepository = new ArtistRepository();
    }

    public LiveData<List<ArtistID3>> getStarredArtists(LifecycleOwner owner) {
        artistRepository.getStarredArtists(false, -1).observe(owner, starredArtists::postValue);
        return starredArtists;
    }

    public LiveData<StarredArtistSongs> getAllStarredArtistSongs() {
        return starredArtistSongs;
    }

    public void refreshAllStarredArtistSongs() {
        if (!checkInFlight.compareAndSet(false, true)) {
            rerunWanted.set(true);
            return;
        }

        artistRepository.getStarredArtistList(artists -> {
            if (artists == null) {
                finishCheck(new StarredArtistSongs(new ArrayList<>(), new ArrayList<>()));
                return;
            }
            collectAllArtistSongs(artists, songs -> finishCheck(new StarredArtistSongs(artists, songs)));
        });
    }

    private void finishCheck(StarredArtistSongs result) {
        checkInFlight.set(false);
        starredArtistSongs.postValue(result);
        if (rerunWanted.getAndSet(false)) {
            refreshAllStarredArtistSongs();
        }
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

    public static final class StarredArtistSongs {
        public final List<ArtistID3> artists;
        public final List<Child> songs;

        StarredArtistSongs(List<ArtistID3> artists, List<Child> songs) {
            this.artists = artists;
            this.songs = songs;
        }
    }
}