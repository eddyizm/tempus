package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.eddyizm.tempus.interfaces.StarCallback;
import com.eddyizm.tempus.model.Chronology;
import com.eddyizm.tempus.model.Favorite;
import com.eddyizm.tempus.model.HomeSector;
import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.ChronologyRepository;
import com.eddyizm.tempus.repository.FavoriteRepository;
import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.repository.SharingRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.subsonic.models.Share;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.Constants.SeedType;
import com.eddyizm.tempus.util.Preferences;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class HomeViewModel extends AndroidViewModel {
    private static final String TAG = "HomeViewModel";

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final ChronologyRepository chronologyRepository;
    private final FavoriteRepository favoriteRepository;
    private final PlaylistRepository playlistRepository;
    private final SharingRepository sharingRepository;

    private final StarredAlbumsSyncViewModel albumsSyncViewModel;
    private final StarredArtistsSyncViewModel artistSyncViewModel;

    private final MutableLiveData<List<Child>> dicoverSongSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> newReleasedAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredTracksSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> starredArtistsSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> bestOfArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredTracks = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> starredAlbums = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> mostPlayedAlbumSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> recentlyPlayedAlbumSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<Integer>> years = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> recentlyAddedAlbumSample = new MutableLiveData<>(null);

    private final MutableLiveData<List<Chronology>> thisGridTopSong = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> mediaInstantMix = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> artistInstantMix = new MutableLiveData<>(null);
    private final MutableLiveData<String> playlistSortOrder = new MutableLiveData<>();
    private final LiveData<List<Playlist>> pinnedPlaylists;
    private final MutableLiveData<List<Share>> shares = new MutableLiveData<>(null);

    private List<HomeSector> sectors;

    private String cachedMusicFolderId = Preferences.getActiveMusicFolderId();
    private int musicFolderGeneration = 0;

    public HomeViewModel(@NonNull Application application) {
        super(application);

        setHomeSectorList();

        songRepository = new SongRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        chronologyRepository = new ChronologyRepository();
        favoriteRepository = new FavoriteRepository();
        playlistRepository = new PlaylistRepository();
        sharingRepository = new SharingRepository();

        albumsSyncViewModel = new StarredAlbumsSyncViewModel(application);
        artistSyncViewModel = new StarredArtistsSyncViewModel(application);

        setOfflineFavorite();

        playlistSortOrder.setValue(Preferences.getHomeSortPlaylists());
        pinnedPlaylists = Transformations.switchMap(playlistSortOrder, sortOrder -> 
            playlistRepository.getSortedPlaylistsPreview(sortOrder, 20)
        );
    }

    public LiveData<List<Child>> getDiscoverSongSample(LifecycleOwner owner) {
        if (dicoverSongSample.getValue() == null) {
            songRepository.getRandomSample(10, null, null).observe(owner, setIfCurrentGeneration(dicoverSongSample));
        }

        return dicoverSongSample;
    }

    public LiveData<List<Child>> getRandomShuffleSample() {
        return songRepository.getRandomSample(100, null, null);
    }

    public LiveData<List<Chronology>> getChronologySample(LifecycleOwner owner) {
        Calendar cal = Calendar.getInstance();
        String server = Preferences.getServerId();

        int currentWeek = cal.get(Calendar.WEEK_OF_YEAR);
        long start = cal.getTimeInMillis();

        cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1);
        long end = cal.getTimeInMillis();

        chronologyRepository.getChronology(server, start, end).observe(owner, thisGridTopSong::postValue);
        return thisGridTopSong;
    }

    public LiveData<List<AlbumID3>> getRecentlyReleasedAlbums(LifecycleOwner owner) {
        if (newReleasedAlbum.getValue() == null) {
            fetchRecentlyReleasedAlbums(owner);
        }

        return newReleasedAlbum;
    }

    private void fetchRecentlyReleasedAlbums(LifecycleOwner owner) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        // Sorts before posting, so it cannot use setIfCurrentGeneration and checks the same way.
        int generation = musicFolderGeneration;

        albumRepository.getAlbums("byYear", 500, currentYear, currentYear).observe(owner, albums -> {
            if (albums != null && generation == musicFolderGeneration) {
                albums.sort(Comparator.comparing(AlbumID3::getCreated,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
                newReleasedAlbum.setValue(albums.subList(0, Math.min(20, albums.size())));
            }
        });
    }

    public LiveData<List<Child>> getStarredTracksSample(LifecycleOwner owner) {
        if (starredTracksSample.getValue() == null) {
            songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue);
        }

        return starredTracksSample;
    }

    public LiveData<List<ArtistID3>> getStarredArtistsSample(LifecycleOwner owner) {
        if (starredArtistsSample.getValue() == null) {
            artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue);
        }

        return starredArtistsSample;
    }

    public LiveData<List<ArtistID3>> getBestOfArtists(LifecycleOwner owner) {
        if (bestOfArtists.getValue() == null) {
            artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue);
        }

        return bestOfArtists;
    }

    public LiveData<List<Child>> getStarredTracks(LifecycleOwner owner) {
        if (starredTracks.getValue() == null) {
            songRepository.getStarredSongs(true, 20).observe(owner, starredTracks::postValue);
        }

        return starredTracks;
    }

    public LiveData<List<AlbumID3>> getStarredAlbums(LifecycleOwner owner) {
        if (starredAlbums.getValue() == null) {
            albumRepository.getStarredAlbums(true, 20).observe(owner, starredAlbums::postValue);
        }

        return starredAlbums;
    }

    public LiveData<List<Child>> getAllStarredAlbumSongs() {
        return albumsSyncViewModel.getAllStarredAlbumSongs();
    }

    public LiveData<List<Child>> getAllStarredArtistSongs() {
        return artistSyncViewModel.getAllStarredArtistSongs();
    }

    public LiveData<List<ArtistID3>> getStarredArtists(LifecycleOwner owner) {
        if (starredArtists.getValue() == null) {
            artistRepository.getStarredArtists(true, 20).observe(owner, starredArtists::postValue);
        }

        return starredArtists;
    }

    public LiveData<List<Integer>> getYearList(LifecycleOwner owner) {
        if (years.getValue() == null) {
            albumRepository.getDecades().observe(owner, setIfCurrentGeneration(years));
        }

        return years;
    }

    public LiveData<List<AlbumID3>> getMostPlayedAlbums(LifecycleOwner owner) {
        if (mostPlayedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("frequent", 20, null, null).observe(owner, setIfCurrentGeneration(mostPlayedAlbumSample));
        }

        return mostPlayedAlbumSample;
    }

    public LiveData<List<AlbumID3>> getMostRecentlyAddedAlbums(LifecycleOwner owner) {
        if (recentlyAddedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("newest", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyAddedAlbumSample));
        }

        return recentlyAddedAlbumSample;
    }

    public LiveData<List<AlbumID3>> getRecentlyPlayedAlbumList(LifecycleOwner owner) {
        if (recentlyPlayedAlbumSample.getValue() == null) {
            albumRepository.getAlbums("recent", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyPlayedAlbumSample));
        }

        return recentlyPlayedAlbumSample;
    }

    public LiveData<List<Child>> getMediaInstantMix(LifecycleOwner owner, Child media) {
        mediaInstantMix.setValue(Collections.emptyList());

        songRepository.getInstantMix(media.getId(), SeedType.TRACK, 20).observe(owner, mediaInstantMix::postValue);

        return mediaInstantMix;
    }

    public LiveData<List<Child>> getArtistInstantMix(LifecycleOwner owner, ArtistID3 artist) {
        artistInstantMix.setValue(Collections.emptyList());

        artistRepository.getTopSongs(artist.getName(), 10).observe(owner, artistInstantMix::postValue);

        return artistInstantMix;
    }

    public LiveData<List<Child>> getArtistBestOf(ArtistID3 artist) {
        MutableLiveData<List<Child>> result = new MutableLiveData<>();
        if (artist == null) {
            result.setValue(new ArrayList<>());
            return result;
        }

        artistRepository.getTopSongs(artist.getName(), 10).observeForever(new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                List<Child> safeSongs = songs != null ? songs : new ArrayList<>();
                result.setValue(safeSongs);
                artistRepository.getTopSongs(artist.getName(), 10).removeObserver(this);
            }
        });

        return result;
    }

    public LiveData<List<Playlist>> getPinnedPlaylists(LifecycleOwner owner) {
        return pinnedPlaylists;
    }

    public void refreshPinnedPlaylists() {
        playlistSortOrder.setValue(Preferences.getHomeSortPlaylists());
    }

    public LiveData<List<Share>> getShares(LifecycleOwner owner) {
        if (shares.getValue() == null) {
            sharingRepository.getShares().observe(owner, shares::postValue);
        }

        return shares;
    }

    public LiveData<List<Child>> getAllStarredTracks() {
        return songRepository.getStarredSongs(false, -1);
    }

    public void changeChronologyPeriod(LifecycleOwner owner, int period) {
        Calendar cal = Calendar.getInstance();
        String server = Preferences.getServerId();
        int currentWeek = cal.get(Calendar.WEEK_OF_YEAR);

        long start = 0;
        long end = 0;

        if (period == 0) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1);
            end = cal.getTimeInMillis();
        } else if (period == 1) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 4);
            end = cal.getTimeInMillis();
        } else if (period == 2) {
            start = cal.getTimeInMillis();
            cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 52);
            end = cal.getTimeInMillis();
        }

        chronologyRepository.getChronology(server, start, end).observe(owner, thisGridTopSong::postValue);
    }

    /**
     * The cached samples were fetched under whichever library was active then, so they go stale
     * when it changes. Clearing them lets the getters' null guards refetch on the next view
     * creation, and every observer of these treats null as "hide this section".
     *
     * Returns whether the library actually changed, so the caller can decide whether the screen it
     * is on needs refetching now as well.
     *
     * Starred data stays by choice, not because it cannot be filtered: getStarred2 does take
     * musicFolderId. getPlaylists does not, and scoping one hand built collection but not the other
     * would behave differently for no reason a user can see.
     */
    public boolean clearCacheIfMusicFolderChanged() {
        String activeMusicFolderId = Preferences.getActiveMusicFolderId();
        if (Objects.equals(activeMusicFolderId, cachedMusicFolderId)) return false;

        cachedMusicFolderId = activeMusicFolderId;
        musicFolderGeneration++;

        dicoverSongSample.setValue(null);
        newReleasedAlbum.setValue(null);
        mostPlayedAlbumSample.setValue(null);
        recentlyPlayedAlbumSample.setValue(null);
        recentlyAddedAlbumSample.setValue(null);
        years.setValue(null);

        return true;
    }

    /**
     * The same staleness, for a screen already on display that will not create its views again.
     * Clearing alone would leave it blank until the user navigated away and back, so this clears
     * and then refetches into the same LiveData the screen is already observing.
     */
    public void reloadIfMusicFolderChanged(LifecycleOwner owner) {
        if (!clearCacheIfMusicFolderChanged()) return;

        // Guarded the same way the init methods in HomeTabMusicFragment are, so reload asks for a
        // sector exactly when that sector was set up. The predicate reports the opposite of its
        // name: true means the id is absent from the saved sector list, which is not the same as
        // the user hiding it, since hiding keeps the entry and only clears its isVisible flag.
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_DISCOVERY)) refreshDiscoverySongSample(owner);
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_NEW_RELEASES)) refreshRecentlyReleasedAlbums(owner);
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_MOST_PLAYED)) refreshMostPlayedAlbums(owner);
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_LAST_PLAYED)) refreshRecentlyPlayedAlbumList(owner);
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_RECENTLY_ADDED)) refreshMostRecentlyAddedAlbums(owner);
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_FLASHBACK)) refreshYearList(owner);
    }

    /**
     * Nothing in the repository layer cancels a request, so a response for the library that was
     * active when it was sent can land after the library has changed and overwrite the new one.
     * Switching twice inside one round trip is enough to leave the wrong library's content under
     * the right library's name, permanently, since the caches then look current.
     *
     * A response from a superseded library is dropped instead of stored. setValue and not
     * postValue: a post would defer the store past the check, and every one of these callbacks
     * already runs on the main thread.
     */
    private <T> Observer<T> setIfCurrentGeneration(MutableLiveData<T> target) {
        int generation = musicFolderGeneration;

        return value -> {
            if (generation == musicFolderGeneration) target.setValue(value);
        };
    }

    public void refreshDiscoverySongSample(LifecycleOwner owner) {
        songRepository.getRandomSample(10, null, null).observe(owner, setIfCurrentGeneration(dicoverSongSample));
    }

    public void refreshSimilarSongSample(LifecycleOwner owner) {
        songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue);
    }

    public void refreshRadioArtistSample(LifecycleOwner owner) {
        artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue);
    }

    public void refreshBestOfArtist(LifecycleOwner owner) {
        artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue);
    }

    public void refreshStarredTracks(LifecycleOwner owner) {
        songRepository.getStarredSongs(true, 20).observe(owner, starredTracks::postValue);
    }

    public void refreshStarredAlbums(LifecycleOwner owner) {
        albumRepository.getStarredAlbums(true, 20).observe(owner, starredAlbums::postValue);
    }

    public void refreshStarredArtists(LifecycleOwner owner) {
        artistRepository.getStarredArtists(true, 20).observe(owner, starredArtists::postValue);
    }

    public void refreshMostPlayedAlbums(LifecycleOwner owner) {
        albumRepository.getAlbums("frequent", 20, null, null).observe(owner, setIfCurrentGeneration(mostPlayedAlbumSample));
    }

    public void refreshMostRecentlyAddedAlbums(LifecycleOwner owner) {
        albumRepository.getAlbums("newest", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyAddedAlbumSample));
    }

    public void refreshRecentlyPlayedAlbumList(LifecycleOwner owner) {
        albumRepository.getAlbums("recent", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyPlayedAlbumSample));
    }

    public void refreshRecentlyReleasedAlbums(LifecycleOwner owner) {
        fetchRecentlyReleasedAlbums(owner);
    }

    public void refreshYearList(LifecycleOwner owner) {
        albumRepository.getDecades().observe(owner, setIfCurrentGeneration(years));
    }

    public void refreshShares(LifecycleOwner owner) {
        sharingRepository.getShares().observe(owner, this.shares::postValue);
    }

    private void setHomeSectorList() {
        if (Preferences.getHomeSectorList() != null && !Preferences.getHomeSectorList().equals("null")) {
            sectors = new Gson().fromJson(
                    Preferences.getHomeSectorList(),
                    new TypeToken<List<HomeSector>>() {
                    }.getType()
            );
        }
    }

    public List<HomeSector> getHomeSectorList() {
        return sectors;
    }

    public boolean checkHomeSectorVisibility(String sectorId) {
        return sectors != null && sectors.stream().filter(sector -> sector.getId().equals(sectorId))
                .findAny()
                .orElse(null) == null;
    }

    public void setOfflineFavorite() {
        ArrayList<Favorite> favorites = getFavorites();
        ArrayList<Favorite> favoritesToSave = getFavoritesToSave(favorites);
        ArrayList<Favorite> favoritesToDelete = getFavoritesToDelete(favorites, favoritesToSave);

        manageFavoriteToSave(favoritesToSave);
        manageFavoriteToDelete(favoritesToDelete);
    }

    private ArrayList<Favorite> getFavorites() {
        return new ArrayList<>(favoriteRepository.getFavorites());
    }

    private ArrayList<Favorite> getFavoritesToSave(ArrayList<Favorite> favorites) {
        HashMap<String, Favorite> filteredMap = new HashMap<>();

        for (Favorite favorite : favorites) {
            String key = favorite.toString();

            if (!filteredMap.containsKey(key) || favorite.getTimestamp() > filteredMap.get(key).getTimestamp()) {
                filteredMap.put(key, favorite);
            }
        }

        return new ArrayList<>(filteredMap.values());
    }

    private ArrayList<Favorite> getFavoritesToDelete(ArrayList<Favorite> favorites, ArrayList<Favorite> favoritesToSave) {
        ArrayList<Favorite> favoritesToDelete = new ArrayList<>();

        for (Favorite favorite : favorites) {
            if (!favoritesToSave.contains(favorite)) {
                favoritesToDelete.add(favorite);
            }
        }

        return favoritesToDelete;
    }

    private void manageFavoriteToSave(ArrayList<Favorite> favoritesToSave) {
        for (Favorite favorite : favoritesToSave) {
            if (favorite.getToStar()) {
                favoriteToStar(favorite);
            } else {
                favoriteToUnstar(favorite);
            }
        }
    }

    private void manageFavoriteToDelete(ArrayList<Favorite> favoritesToDelete) {
        for (Favorite favorite : favoritesToDelete) {
            favoriteRepository.delete(favorite);
        }
    }

    private StarCallback dropWhenAnswered(Favorite favorite) {
        return new StarCallback() {
            @Override
            public void onSuccess() {
                favoriteRepository.delete(favorite);
            }

            @Override
            public void onRefused() {
                favoriteRepository.delete(favorite);
            }
        };
    }

    private void favoriteToStar(Favorite favorite) {
        if (favorite.getSongId() != null) {
            favoriteRepository.star(favorite.getSongId(), null, null, dropWhenAnswered(favorite));
        } else if (favorite.getAlbumId() != null) {
            favoriteRepository.star(null, favorite.getAlbumId(), null, dropWhenAnswered(favorite));
        } else if (favorite.getArtistId() != null) {
            favoriteRepository.star(null, null, favorite.getArtistId(), dropWhenAnswered(favorite));
        }
    }

    private void favoriteToUnstar(Favorite favorite) {
        if (favorite.getSongId() != null) {
            favoriteRepository.unstar(favorite.getSongId(), null, null, dropWhenAnswered(favorite));
        } else if (favorite.getAlbumId() != null) {
            favoriteRepository.unstar(null, favorite.getAlbumId(), null, dropWhenAnswered(favorite));
        } else if (favorite.getArtistId() != null) {
            favoriteRepository.unstar(null, null, favorite.getArtistId(), dropWhenAnswered(favorite));
        }
    }
}
