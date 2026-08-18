package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Child;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class StarredAlbumsSyncViewModel extends AndroidViewModel {
    private final AlbumRepository albumRepository;

    private final MutableLiveData<List<AlbumID3>> starredAlbums = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredAlbumSongs = new MutableLiveData<>(null);

    public StarredAlbumsSyncViewModel(@NonNull Application application) {
        super(application);
        albumRepository = new AlbumRepository();
    }

    public LiveData<List<AlbumID3>> getStarredAlbums(LifecycleOwner owner) {
        albumRepository.getStarredAlbums(false, -1).observe(owner, starredAlbums::postValue);
        return starredAlbums;
    }

    public LiveData<List<Child>> getAllStarredAlbumSongs() {
        albumRepository.getStarredAlbums(false, -1).observeForever(new Observer<List<AlbumID3>>() {
            @Override
            public void onChanged(List<AlbumID3> albums) {
                if (albums != null && !albums.isEmpty()) {
                    collectAllAlbumSongs(albums, starredAlbumSongs::postValue);
                } else {
                    starredAlbumSongs.postValue(new ArrayList<>());
                }
                albumRepository.getStarredAlbums(false, -1).removeObserver(this);
            }
        });
        
        return starredAlbumSongs;
    }

    public LiveData<List<Child>> getStarredAlbumSongs(Activity activity) {
        albumRepository.getStarredAlbums(false, -1).observe((LifecycleOwner) activity, albums -> {
            if (albums != null && !albums.isEmpty()) {
                collectAllAlbumSongs(albums, starredAlbumSongs::postValue);
            } else {
                starredAlbumSongs.postValue(new ArrayList<>());
            }
        });
        return starredAlbumSongs;
    }

    private void collectAllAlbumSongs(List<AlbumID3> albums, AlbumSongsCallback callback) {
        List<Child> allSongs = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(albums.size());
        
        for (AlbumID3 album : albums) {
            LiveData<List<Child>> albumTracks = albumRepository.getAlbumTracks(album.getId());
            albumTracks.observeForever(new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> songs) {
                    if (songs != null) {
                        allSongs.addAll(songs);
                    }
                    latch.countDown();
                    
                    if (latch.getCount() == 0) {
                        callback.onSongsCollected(allSongs);
                        albumTracks.removeObserver(this);
                    }
                }
            });
        }
    }

    private interface AlbumSongsCallback {
        void onSongsCollected(List<Child> songs);
    }
}