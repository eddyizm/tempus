package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.interfaces.StarCallback;
import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.FavoriteRepository;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.NetworkUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.StarredSync;
import com.eddyizm.tempus.util.FavoriteRegistry;

import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ArtistBottomSheetViewModel extends AndroidViewModel {
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;
    private final MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(null);
    private LiveData<List<Child>> artistAllTracks;

    private ArtistID3 artist;

    public ArtistBottomSheetViewModel(@NonNull Application application) {
        super(application);

        artistRepository = new ArtistRepository();
        favoriteRepository = new FavoriteRepository();
    }

    public ArtistID3 getArtist() {
        return artist;
    }

    public void setArtist(ArtistID3 artist) {
        this.artist = artist;
        this.artistAllTracks = null; // reset cache on new artist
    }

    /**
     * Lazily fetches and caches all tracks for the current artist.
     * Safe to call multiple times — only one network batch is started per artist.
     */
    public LiveData<List<Child>> getArtistAllTracks() {
        if (artistAllTracks == null) {
            artistAllTracks = artistRepository.getArtistAllTracksLive(artist.getId());
        }
        return artistAllTracks;
    }

    public void setFavorite(Context context) {
        if (FavoriteRegistry.resolve(FavoriteRegistry.Kind.ARTIST, artist.getId(), artist.getStarred() != null)) {
            if (NetworkUtil.isOffline()) {
                removeFavoriteOffline();
            } else {
                removeFavoriteOnline();
            }
        } else {
            if (NetworkUtil.isOffline()) {
                setFavoriteOffline(context);
            } else {
                setFavoriteOnline(context);
            }
        }
    }

    private void removeFavoriteOffline() {
        favoriteRepository.starLater(null, null, artist.getId(), false);
        artist.setStarred(null);
    }

    private void removeFavoriteOnline() {
        favoriteRepository.unstar(null, null, artist.getId(), new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(null, null, artist.getId(), false);
            }
        });

        artist.setStarred(null);
    }

    private void setFavoriteOffline(Context context) {
        favoriteRepository.starLater(null, null, artist.getId(), true);
        artist.setStarred(new Date());
    }

    private void setFavoriteOnline(Context context) {
        favoriteRepository.star(null, null, artist.getId(), new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(null, null, artist.getId(), true);
            }
        });

        artist.setStarred(new Date());
        
        Log.d("ArtistSync", "Checking preference: " + Preferences.isStarredArtistsSyncEnabled());
        
        if (Preferences.isStarredArtistsSyncEnabled()) {
            Log.d("ArtistSync", "Starting artist sync for: " + artist.getName());
            
            StarredSync.syncArtist(context, artist.getId());
        } else {
            Log.d("ArtistSync", "Artist sync preference is disabled");
        }
    }
    
    public LiveData<List<Child>> getArtistInstantMix(LifecycleOwner owner, ArtistID3 artist) {
        instantMix.setValue(Collections.emptyList());

        artistRepository.getInstantMix(artist, 30).observe(owner, instantMix::postValue);

        return instantMix;
    }
}
