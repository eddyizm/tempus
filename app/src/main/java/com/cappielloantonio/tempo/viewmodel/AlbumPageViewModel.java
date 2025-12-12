package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.AlbumInfo;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.NetworkUtil;

import java.util.Date;
import java.util.List;

public class AlbumPageViewModel extends AndroidViewModel {
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;
    private String albumId;
    private String artistId;
    private final MutableLiveData<AlbumID3> album = new MutableLiveData<>(null);

    public AlbumPageViewModel(@NonNull Application application) {
        super(application);

        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        favoriteRepository = new FavoriteRepository();
    }

    public LiveData<List<Child>> getAlbumSongLiveList() {
        return albumRepository.getAlbumTracks(albumId);
    }

    public MutableLiveData<AlbumID3> getAlbum() {
        return album;
    }

    public void setAlbum(LifecycleOwner owner, AlbumID3 album) {
        this.albumId = album.getId();
        this.album.postValue(album);
        this.artistId = album.getArtistId();

        albumRepository.getAlbum(album.getId()).observe(owner, albums -> {
            if (albums != null) this.album.setValue(albums);
        });
    }

    public void setFavorite() {
        AlbumID3 currentAlbum = album.getValue();
        if (currentAlbum == null) return;
        
        if (currentAlbum.getStarred() != null) {
            if (NetworkUtil.isOffline()) {
                removeFavoriteOffline(currentAlbum);
            } else {
                removeFavoriteOnline(currentAlbum);
            }
        } else {
            if (NetworkUtil.isOffline()) {
                setFavoriteOffline(currentAlbum);
            } else {
                setFavoriteOnline(currentAlbum);
            }
        }
    }

    private void removeFavoriteOffline(AlbumID3 album) {
        favoriteRepository.starLater(null, album.getId(), null, false);
        album.setStarred(null);
        this.album.postValue(album);
    }

    private void removeFavoriteOnline(AlbumID3 album) {
        favoriteRepository.unstar(null, album.getId(), null, new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(null, album.getId(), null, false);
            }
        });

        album.setStarred(null);
        this.album.postValue(album);
    }

    private void setFavoriteOffline(AlbumID3 album) {
        favoriteRepository.starLater(null, album.getId(), null, true);
        album.setStarred(new Date());
        this.album.postValue(album);
    }

    private void setFavoriteOnline(AlbumID3 album) {
        favoriteRepository.star(null, album.getId(), null, new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(null, album.getId(), null, true);
            }
        });

        album.setStarred(new Date());
        this.album.postValue(album);
    }

    public LiveData<ArtistID3> getArtist() {
        return artistRepository.getArtistInfo(artistId);
    }

    public LiveData<AlbumInfo> getAlbumInfo() {
        return albumRepository.getAlbumInfo(albumId);
    }
}
