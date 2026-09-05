package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.interfaces.MediaCallback;
import com.eddyizm.tempus.interfaces.StarCallback;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.FavoriteRepository;
import com.eddyizm.tempus.repository.SharingRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Share;
import com.eddyizm.tempus.util.Constants.SeedType;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.MappingUtil;
import com.eddyizm.tempus.util.NetworkUtil;
import com.eddyizm.tempus.util.Preferences;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@UnstableApi
public class SongBottomSheetViewModel extends AndroidViewModel {
    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;
    private final SharingRepository sharingRepository;

    private Child song;

    private final MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(null);

    public SongBottomSheetViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        favoriteRepository = new FavoriteRepository();
        sharingRepository = new SharingRepository();
    }

    public Child getSong() {
        return song;
    }

    public void setSong(Child song) {
        this.song = song;
    }

    public void setFavorite(Context context) {
        if (song.getStarred() != null) {
            if (NetworkUtil.isOffline()) {
                removeFavoriteOffline(song);
            } else {
                removeFavoriteOnline(song);
            }
        } else {
            if (NetworkUtil.isOffline()) {
                setFavoriteOffline(song);
            } else {
                setFavoriteOnline(context, song);
            }
        }
    }

    private void removeFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, false);
        media.setStarred(null);
    }

    private void removeFavoriteOnline(Child media) {
        favoriteRepository.unstar(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                // media.setStarred(new Date());
                favoriteRepository.starLater(media.getId(), null, null, false);
            }
        });

        media.setStarred(null);
    }

    private void setFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, true);
        media.setStarred(new Date());
    }

    private void setFavoriteOnline(Context context, Child media) {
        favoriteRepository.star(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                // media.setStarred(null);
                favoriteRepository.starLater(media.getId(), null, null, true);
            }
        });

        media.setStarred(new Date());

        if (Preferences.isStarredSyncEnabled() && Preferences.getDownloadDirectoryUri() == null) {
            DownloadUtil.getDownloadTracker(context).download(
                    MappingUtil.mapDownload(media),
                    new Download(media)
            );
        }
    }

    public LiveData<AlbumID3> getAlbum() {
        return albumRepository.getAlbum(song.getAlbumId());
    }

    public LiveData<ArtistID3> getArtist() {
        return artistRepository.getArtist(song.getArtistId());
    }

    public LiveData<List<Child>> getInstantMix(LifecycleOwner owner, Child media) {
        instantMix.setValue(Collections.emptyList());

        songRepository.getInstantMix(media.getId(), SeedType.TRACK, 30).observe(owner, instantMix::postValue);

        return instantMix;
    }

    public void getInstantMix(Child media, int count, MediaCallback callback) {
        
        songRepository.getInstantMix(media.getId(), SeedType.TRACK, count, songs -> {
            if (songs != null && !songs.isEmpty()) {
                callback.onLoadMedia(songs);
            } else {
                callback.onLoadMedia(Collections.emptyList());
            }
        });
    }

    public MutableLiveData<Share> shareTrack() {
        return sharingRepository.createShare(song.getId(), song.getTitle(), null);
    }
}
