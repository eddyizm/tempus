package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistInfo2;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ArtistPageViewModel extends AndroidViewModel {
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;

    private ArtistID3 artist;

    private final MutableLiveData<List<AlbumID3>> singles = new MutableLiveData<>();
    private final MutableLiveData<List<AlbumID3>> eps = new MutableLiveData<>();
    private final MutableLiveData<List<AlbumID3>> mainAlbums = new MutableLiveData<>();
    private final MutableLiveData<List<AlbumID3>> appearsOn = new MutableLiveData<>();

    public ArtistPageViewModel(@NonNull Application application) {
        super(application);

        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        favoriteRepository = new FavoriteRepository();
    }

    public void fetchCategorizedAlbums(androidx.lifecycle.LifecycleOwner owner) {
        artistRepository.getArtist(artist.getId()).observe(owner, artistWithAlbums -> {
            if (artistWithAlbums != null && artistWithAlbums instanceof com.cappielloantonio.tempo.subsonic.models.ArtistWithAlbumsID3) {
                com.cappielloantonio.tempo.subsonic.models.ArtistWithAlbumsID3 fullArtist = (com.cappielloantonio.tempo.subsonic.models.ArtistWithAlbumsID3) artistWithAlbums;
                
                List<AlbumID3> allAlbums = fullArtist.getAlbums();
                if (allAlbums != null) {
                    allAlbums.sort(Comparator.comparing(AlbumID3::getYear).reversed());
                    
                    mainAlbums.setValue(allAlbums.stream()
                        .filter(a ->
                                isType(a, "album") && Objects.equals(a.getArtistId(), artist.getId()))
                        .collect(Collectors.toList()));
                    
                    singles.setValue(allAlbums.stream()
                        .filter(a ->
                                isType(a, "single") && Objects.equals(a.getArtistId(), artist.getId()))
                        .collect(Collectors.toList()));
                    
                    eps.setValue(allAlbums.stream()
                        .filter(a ->
                                isType(a, "ep") && Objects.equals(a.getArtistId(), artist.getId()))
                        .collect(Collectors.toList()));
                }
                if (allAlbums != null) {
                    allAlbums.sort(Comparator.comparing(AlbumID3::getYear).reversed());

                    appearsOn.setValue(allAlbums.stream()
                            .filter(a -> !Objects.equals(a.getArtistId(), artist.getId()))
                            .collect(Collectors.toList())
                    );
                }
            }
        });
    }

    private boolean isType(AlbumID3 album, String targetType) {
        if (album.getReleaseTypes() != null && !album.getReleaseTypes().isEmpty()) {
            return album.getReleaseTypes().contains(targetType);
        }
        // Fallback to song count if releaseTypes is not available
        int songCount = album.getSongCount() != null ? album.getSongCount() : 0;
        switch (targetType) {
            case "single":
                return songCount >= 1 && songCount <= 2;
            case "ep":
                return songCount >= 3 && songCount <= 7;
            case "album":
                return songCount >= 8;
            default:
                return false;
        }
    }

    public LiveData<List<AlbumID3>> getSingles() { return singles; }
    public LiveData<List<AlbumID3>> getEPs() { return eps; }
    public LiveData<List<AlbumID3>> getMainAlbums() { return mainAlbums; }
    public LiveData<List<AlbumID3>> getAppearsOn() { return appearsOn; }

    public LiveData<List<AlbumID3>> getAlbumList() {
        return albumRepository.getArtistAlbums(artist.getId());
    }

    public LiveData<ArtistInfo2> getArtistInfo(String id) {
        return artistRepository.getArtistFullInfo(id);
    }

    public LiveData<List<Child>> getArtistTopSongList() {
        return artistRepository.getTopSongs(artist.getName(), 20);
    }

    public LiveData<List<Child>> getArtistShuffleList() {
        return artistRepository.getRandomSong(artist, 50);
    }

    public LiveData<List<Child>> getArtistInstantMix() {
        return artistRepository.getInstantMix(artist, 30);
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
                setFavoriteOffline();
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

        private void setFavoriteOffline() {
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

        if (Preferences.isStarredArtistsSyncEnabled()) {
            artistRepository.getArtistAllSongs(artist.getId(), new ArtistRepository.ArtistSongsCallback() {
                @OptIn(markerClass = UnstableApi.class)
                @Override
                public void onSongsCollected(List<Child> songs) {
                    if (songs != null && !songs.isEmpty()) {
                        DownloadUtil.getDownloadTracker(context).download(
                                MappingUtil.mapDownloads(songs),
                                songs.stream().map(Download::new).collect(Collectors.toList())
                        );
                    }
                }
            });
        } else {
            Log.d("ArtistSync", "Artist sync preference is disabled");
        }
    }

}
