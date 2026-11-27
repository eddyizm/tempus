package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.SearchingRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Genre;
import com.eddyizm.tempus.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SongListPageViewModel extends AndroidViewModel {
    /** Page size for server-side paging. Kept below common server caps (1000). */
    static final int PAGE_SIZE = 100;

    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final SearchingRepository searchingRepository;

    private final MutableLiveData<List<Child>> songList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);

    private boolean loaded = false;
    private long requestId = 0;

    // Pagination state. Only titles with a stable, pageable server endpoint use it.
    private int nextPage = 0;
    private boolean hasMore = false;
    private boolean paging = false;

    public String title;
    public String toolbarTitle;
    /** Sort key chosen by the user, kept here so it survives refresh and rotation. */
    public String currentSort;
    public Genre genre;
    public ArtistID3 artist;
    public AlbumID3 album;

    public ArrayList<String> filters = new ArrayList<>();
    public ArrayList<String> filterNames = new ArrayList<>();

    public int year = 0;
    public int maxNumberByYear = 500;

    public SongListPageViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        artistRepository = new ArtistRepository();
        searchingRepository = new SearchingRepository();
    }

    /**
     * Returns one stable LiveData for the lifetime of this screen. Querying again is done
     * explicitly through {@link #refresh()}, so observers are never re-registered and
     * pull-to-refresh actually re-runs the request.
     */
    public LiveData<List<Child>> getSongList() {
        if (!loaded) {
            loaded = true;
            refresh();
        }
        return songList;
    }

    public LiveData<Boolean> getRefreshing() {
        return refreshing;
    }

    public LiveData<Boolean> getLoadingMore() {
        return loadingMore;
    }

    /** True while another page can be requested and no initial/follow-up request is running. */
    public boolean hasMorePages() {
        return hasMore && !paging && !Boolean.TRUE.equals(refreshing.getValue());
    }

    private void resetPagination() {
        nextPage = 0;
        hasMore = false;
        paging = false;
        loadingMore.setValue(false);
    }

    public void refresh() {
        final long id = ++requestId;
        refreshing.setValue(true);
        resetPagination();

        Consumer<List<Child>> onResult = songs -> {
            if (id != requestId) return;
            List<Child> result = songs != null ? new ArrayList<>(songs) : new ArrayList<>();
            // Set paging state before emitting the list so observers see the final state.
            if (isPageable()) {
                nextPage = 1;
                hasMore = result.size() == PAGE_SIZE;
            } else {
                hasMore = false;
            }
            songList.setValue(result);
            refreshing.setValue(false);
        };

        switch (title == null ? "" : title) {
            case Constants.MEDIA_ALL:
                searchingRepository.fetchAllSongsPage(0, PAGE_SIZE, onResult);
                break;
            case Constants.MEDIA_BY_GENRE:
                songRepository.fetchSongsByGenrePage(genre.getGenre(), 0, PAGE_SIZE, onResult);
                break;
            case Constants.MEDIA_BY_ARTIST:
                artistRepository.fetchTopSongs(artist.getName(), 50, onResult);
                break;
            case Constants.MEDIA_BY_GENRES:
                songRepository.fetchSongsByGenres(filters, onResult);
                break;
            case Constants.MEDIA_BY_YEAR:
                songRepository.fetchRandomSample(maxNumberByYear, year, year + 10, onResult);
                break;
            case Constants.MEDIA_STARRED:
                songRepository.fetchStarredSongs(onResult);
                break;
            default:
                // Titles without a dedicated query keep the previous behaviour: an empty list.
                songList.setValue(new ArrayList<>());
                refreshing.setValue(false);
                break;
        }
    }

    /** Only endpoints with a stable ordering may be paged; random samples would duplicate songs. */
    private boolean isPageable() {
        return Constants.MEDIA_BY_GENRE.equals(title) || Constants.MEDIA_ALL.equals(title);
    }

    /**
     * Appends the next server page. Safe to call repeatedly: the in-flight guard prevents
     * overlapping requests, and stale callbacks from before a refresh are ignored.
     */
    public void loadNextPage() {
        if (!hasMorePages()) return;

        final long id = requestId;
        final int page = nextPage++;
        paging = true;
        loadingMore.setValue(true);

        Consumer<List<Child>> onResult = songs -> {
            if (id != requestId) return;
            paging = false;
            loadingMore.setValue(false);

            List<Child> result = songs != null ? songs : Collections.emptyList();
            if (result.size() < PAGE_SIZE) {
                hasMore = false;
            }
            appendUnique(result);
        };

        if (Constants.MEDIA_ALL.equals(title)) {
            searchingRepository.fetchAllSongsPage(page * PAGE_SIZE, PAGE_SIZE, onResult);
        } else if (Constants.MEDIA_BY_GENRE.equals(title)) {
            songRepository.fetchSongsByGenrePage(genre.getGenre(), page, PAGE_SIZE, onResult);
        } else {
            // Defensive: isPageable() should have kept hasMore false for anything else.
            paging = false;
            loadingMore.setValue(false);
            hasMore = false;
        }
    }

    private void appendUnique(List<Child> page) {
        if (page == null || page.isEmpty()) return;

        Map<String, Child> unique = new LinkedHashMap<>();
        List<Child> current = songList.getValue();
        if (current != null) {
            for (Child song : current) {
                unique.put(song.getId(), song);
            }
        }
        for (Child song : page) {
            unique.put(song.getId(), song);
        }
        songList.setValue(new ArrayList<>(unique.values()));
    }

    public String getFiltersTitle() {
        return TextUtils.join(", ", filterNames);
    }
}
