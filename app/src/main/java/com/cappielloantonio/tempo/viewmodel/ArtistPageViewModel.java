package com.cappielloantonio.tempo.viewmodel;

import static java.util.stream.Collectors.groupingBy;

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

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ArtistPageViewModel extends AndroidViewModel {
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final FavoriteRepository favoriteRepository;

    private ArtistID3 artist;

    private final MutableLiveData<List<AlbumID3>> appearsOn = new MutableLiveData<>();
    private final MutableLiveData<Map<String, List<AlbumID3>>> mapOfAlbums = new MutableLiveData<>();

    public ArtistPageViewModel(@NonNull Application application) {
        super(application);

        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        favoriteRepository = new FavoriteRepository();
    }

    public void fetchCategorizedAlbums(androidx.lifecycle.LifecycleOwner owner) {
        artistRepository.getArtist(artist.getId()).observe(owner, artistWithAlbums -> {
            if (artistWithAlbums != null && artistWithAlbums instanceof com.cappielloantonio.tempo.subsonic.models.ArtistWithAlbumsID3) {
                java.util.function.Predicate<AlbumID3> sameArtist = a -> Objects.equals(a.getArtistId(), Objects.requireNonNull(artist.getId()));
                com.cappielloantonio.tempo.subsonic.models.ArtistWithAlbumsID3 fullArtist = (com.cappielloantonio.tempo.subsonic.models.ArtistWithAlbumsID3) artistWithAlbums;
                List<AlbumID3> allAlbums = fullArtist.getAlbums();
                if (allAlbums != null) {
                    allAlbums.sort(Comparator.comparing(AlbumID3::getYear).reversed());

                    mapOfAlbums.setValue(
                            allAlbums.stream()
                                    .collect(groupingBy(a -> {
                                        Iterator<String> releaseTypes = a.getReleaseTypes().iterator();
                                        return releaseTypes.hasNext() ? releaseTypes.next().toLowerCase() : getAutoType(a);
                                    })));

                } else {
                    mapOfAlbums.setValue(Collections.emptyMap());
                }
                if (allAlbums != null) {
                    allAlbums.sort(Comparator.comparing(AlbumID3::getYear).reversed());

                    appearsOn.setValue(allAlbums.stream()
                            .filter(a -> !sameArtist.test(a))
                            .collect(Collectors.toList())
                    );
                }
            }
        });
    }

    private String getAutoType(AlbumID3 album) {
        // Fallback to song count if releaseTypes is not available
        int songCount = album.getSongCount() != null ? album.getSongCount() : 0;
        if (songCount >= 8) {
            return "album";
        } else if (songCount <= 2) {
            return "single";
        } else {
            return "ep";
        }
    }

    public LiveData<Map<String, List<AlbumID3>>> getMapOfAlbums() { return mapOfAlbums; }
    public LiveData<List<AlbumID3>> getAppearsOn() { return appearsOn; }

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
