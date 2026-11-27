package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Genre;
import com.eddyizm.tempus.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SongListPageViewModel extends AndroidViewModel {
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;

    private final MutableLiveData<List<Child>> songList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>(false);

    private boolean loaded = false;
    private long requestId = 0;

    public String title;
    public String toolbarTitle;
    public Genre genre;
    public ArtistID3 artist;
    public AlbumID3 album;

    public ArrayList<String> filters = new ArrayList<>();
    public ArrayList<String> filterNames = new ArrayList<>();

    public int year = 0;
    public int maxNumberByYear = 500;
    public int maxNumberByGenre = 500;

    public SongListPageViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        artistRepository = new ArtistRepository();
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

    public void refresh() {
        final long id = ++requestId;
        refreshing.setValue(true);

        Consumer<List<Child>> onResult = songs -> {
            if (id != requestId) return;
            songList.setValue(songs != null ? new ArrayList<>(songs) : new ArrayList<>());
            refreshing.setValue(false);
        };

        switch (title == null ? "" : title) {
            case Constants.MEDIA_BY_GENRE:
                songRepository.fetchRandomSampleWithGenre(maxNumberByGenre, 0, 3000, genre.getGenre(), onResult);
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

    public void getSongsByPage(LifecycleOwner owner) {
        switch (title) {
            case Constants.MEDIA_BY_GENRE:
                int songCount = songList.getValue() != null ? songList.getValue().size() : 0;

                if (songCount > 0 && songCount % maxNumberByGenre != 0) return;

                int page = songCount / maxNumberByGenre;
                songRepository.getSongsByGenre(genre.getGenre(), page).observe(owner, children -> {
                    if (children != null && !children.isEmpty()) {
                        List<Child> currentMedia = songList.getValue();
                        currentMedia.addAll(children);
                        songList.setValue(currentMedia);
                    }
                });
                break;
            case Constants.MEDIA_BY_ARTIST:
            case Constants.MEDIA_BY_GENRES:
            case Constants.MEDIA_BY_YEAR:
            case Constants.MEDIA_STARRED:
                break;
        }
    }

    public String getFiltersTitle() {
        return TextUtils.join(", ", filterNames);
    }
}
