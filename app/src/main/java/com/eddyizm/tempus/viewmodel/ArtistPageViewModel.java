package com.eddyizm.tempus.viewmodel;

import static java.util.stream.Collectors.groupingBy;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.interfaces.StarCallback;
import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.FavoriteRepository;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.ArtistInfo2;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.NetworkUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.StarredSync;
import com.eddyizm.tempus.util.FavoriteRegistry;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
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

    public void fetchCategorizedAlbums(LifecycleOwner owner) {
        fetchArtistAlbums(owner).observe(owner, albums -> mapOfAlbums.setValue(
                groupAlbums(albums, Preferences.isArtistAlbumsCategorized())));
    }

    /**
     * Fetch this artist's own albums without any release-type classification.
     * Both the categorized shelves and the flat Albums shelf go through here, so
     * sorting and "Appears On" stay identical when the setting changes.
     */
    public LiveData<List<AlbumID3>> fetchArtistAlbums(LifecycleOwner owner) {
        MutableLiveData<List<AlbumID3>> rawAlbums = new MutableLiveData<>();

        artistRepository.getArtist(artist.getId()).observe(owner, artistWithAlbums -> {
            List<AlbumID3> allAlbums = null;
            if (artistWithAlbums instanceof com.eddyizm.tempus.subsonic.models.ArtistWithAlbumsID3) {
                allAlbums = ((com.eddyizm.tempus.subsonic.models.ArtistWithAlbumsID3) artistWithAlbums).getAlbums();
            }

            if (allAlbums == null) {
                rawAlbums.setValue(Collections.emptyList());
                appearsOn.setValue(Collections.emptyList());
                return;
            }

            allAlbums.sort(Comparator.comparing(AlbumID3::getYear).reversed());
            Predicate<AlbumID3> sameArtist = a -> Objects.equals(a.getArtistId(), artist.getId());

            rawAlbums.setValue(allAlbums.stream()
                    .filter(sameArtist)
                    .collect(Collectors.toList()));
            appearsOn.setValue(allAlbums.stream()
                    .filter(a -> !sameArtist.test(a))
                    .collect(Collectors.toList()));
        });

        return rawAlbums;
    }

    /** The classification choke point: one Albums shelf when grouping is switched off. */
    static Map<String, List<AlbumID3>> groupAlbums(List<AlbumID3> albums, boolean categorized) {
        if (albums == null || albums.isEmpty()) {
            return Collections.emptyMap();
        }
        if (!categorized) {
            return Collections.singletonMap("album", albums);
        }
        return albums.stream().collect(groupingBy(ArtistPageViewModel::sectionType));
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
            StarredSync.syncArtist(context, artist.getId());
        } else {
            Log.d("ArtistSync", "Artist sync preference is disabled");
        }
    }

}
