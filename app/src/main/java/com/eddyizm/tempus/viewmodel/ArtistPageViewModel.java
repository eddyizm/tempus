package com.eddyizm.tempus.viewmodel;

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

import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.interfaces.StarCallback;
import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.FavoriteRepository;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.ArtistInfo2;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.MappingUtil;
import com.eddyizm.tempus.util.NetworkUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.FavoriteRegistry;

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
            if (artistWithAlbums != null && artistWithAlbums instanceof com.eddyizm.tempus.subsonic.models.ArtistWithAlbumsID3) {
                java.util.function.Predicate<AlbumID3> sameArtist = a -> Objects.equals(a.getArtistId(), Objects.requireNonNull(artist.getId()));
                com.eddyizm.tempus.subsonic.models.ArtistWithAlbumsID3 fullArtist = (com.eddyizm.tempus.subsonic.models.ArtistWithAlbumsID3) artistWithAlbums;
                List<AlbumID3> allAlbums = fullArtist.getAlbums();
                if (allAlbums != null) {
                    allAlbums.sort(Comparator.comparing(AlbumID3::getYear).reversed());

                    List<AlbumID3> primaryAlbums = allAlbums.stream()
                            .filter(sameArtist)
                            .collect(Collectors.toList());
                    mapOfAlbums.setValue(
                            primaryAlbums.stream()
                                    .collect(groupingBy(ArtistPageViewModel::sectionType)));

                } else {
                    mapOfAlbums.setValue(Collections.emptyMap());
                    appearsOn.setValue(Collections.emptyList());
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

    /**
     * The section an album is filed under, never blank: AlbumSectionsAdapter titles a
     * section outside its own list from the first character of this value.
     */
    static String sectionType(AlbumID3 album) {
        List<String> releaseTypes = album.getReleaseTypes();

        if (releaseTypes != null && !releaseTypes.isEmpty() && releaseTypes.get(0) != null) {
            String releaseType = releaseTypes.get(0).trim().toLowerCase();
            if (!releaseType.isEmpty()) {
                return releaseType;
            }
        }

        return getAutoType(album);
    }

    private static String getAutoType(AlbumID3 album) {
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
        if (FavoriteRegistry.resolve(FavoriteRegistry.Kind.ARTIST, artist.getId(), artist.getStarred() != null)) {
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
