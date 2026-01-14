package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.Collections;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.List;

public class ArtistBottomSheetViewModel extends AndroidViewModel {
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;
    private final MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(null);

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
    }

    public void setFavorite(Context context) {
        if (artist.getStarred() != null) {
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
            
            artistRepository.getArtistAllSongs(artist.getId(), new ArtistRepository.ArtistSongsCallback() {
                @OptIn(markerClass = UnstableApi.class)
                @Override
                public void onSongsCollected(List<Child> songs) {
                    Log.d("ArtistSync", "Callback triggered with songs: " + (songs != null ? songs.size() : 0));
                    if (songs != null && !songs.isEmpty()) {
                        Log.d("ArtistSync", "Starting download of " + songs.size() + " songs");
                        DownloadUtil.getDownloadTracker(context).download(
                                MappingUtil.mapDownloads(songs),
                                songs.stream().map(Download::new).collect(Collectors.toList())
                        );
                        Log.d("ArtistSync", "Download started successfully");
                    } else {
                        Log.d("ArtistSync", "No songs to download");
                    }
                }
            });
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
