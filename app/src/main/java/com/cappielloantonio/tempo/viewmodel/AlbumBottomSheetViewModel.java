package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.repository.SharingRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Share;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.Date;
import java.util.List;

public class AlbumBottomSheetViewModel extends AndroidViewModel {
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;
    private final SharingRepository sharingRepository;

    private AlbumID3 album;

    public AlbumBottomSheetViewModel(@NonNull Application application) {
        super(application);

        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        favoriteRepository = new FavoriteRepository();
        sharingRepository = new SharingRepository();
    }

    public AlbumID3 getAlbum() {
        return album;
    }

    public void setAlbum(AlbumID3 album) {
        this.album = album;
    }

    public LiveData<ArtistID3> getArtist() {
        return artistRepository.getArtist(album.getArtistId());
    }

    public MutableLiveData<List<Child>> getAlbumTracks() {
        return albumRepository.getAlbumTracks(album.getId());
    }

    public void setFavorite(Context context) {
        if (album.getStarred() != null) {
            if (NetworkUtil.isOffline()) {
                removeFavoriteOffline();
            } else {
                removeFavoriteOnline();
            }
        } else {
            if (NetworkUtil.isOffline()) {
                setFavoriteOffline();
            } else {
                setFavoriteOnline(context);
            }
        }
    }

    public MutableLiveData<Share> shareAlbum() {
        return sharingRepository.createShare(album.getId(), album.getName(), null);
    }

    private void removeFavoriteOffline() {
        favoriteRepository.starLater(null, album.getId(), null, false);
        album.setStarred(null);
    }

    private void removeFavoriteOnline() {
        favoriteRepository.unstar(null, album.getId(), null, new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(null, album.getId(), null, false);
            }
        });

        album.setStarred(null);
    }

    private void setFavoriteOffline() {
        favoriteRepository.starLater(null, album.getId(), null, true);
        album.setStarred(new Date());
    }

    private void setFavoriteOnline(Context context) {
        favoriteRepository.star(null, album.getId(), null, new StarCallback() {
            @Override
            public void onError() {
                favoriteRepository.starLater(null, album.getId(), null, true);
            }
        });

        album.setStarred(new Date());
        if (Preferences.isStarredAlbumsSyncEnabled()) {
                AlbumRepository albumRepository = new AlbumRepository();
                MutableLiveData<List<Child>> tracksLiveData = albumRepository.getAlbumTracks(album.getId());
                
                tracksLiveData.observeForever(new Observer<List<Child>>() {
                    @Override
                    public void onChanged(List<Child> songs) {
                        if (songs != null && !songs.isEmpty()) {
                            DownloadUtil.getDownloadTracker(context).download(songs);
                        }
                        tracksLiveData.removeObserver(this);
                    }
                });
            }
    }
}
