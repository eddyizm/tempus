package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.repository.DirectoryRepository;
import com.eddyizm.tempus.repository.GenreRepository;
import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Genre;
import com.eddyizm.tempus.subsonic.models.Indexes;
import com.eddyizm.tempus.subsonic.models.MusicFolder;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.util.Preferences;

import java.util.List;
import java.util.Objects;

public class LibraryViewModel extends AndroidViewModel {
    private static final String TAG = "LibraryViewModel";

    private final DirectoryRepository directoryRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final PlaylistRepository playlistRepository;

    private final MutableLiveData<List<MusicFolder>> musicFolders = new MutableLiveData<>(null);
    private final MutableLiveData<Indexes> indexes = new MutableLiveData<>(null);
    private final MutableLiveData<List<Playlist>> playlistSample = new MutableLiveData<>(null);
    private final MutableLiveData<List<AlbumID3>> sampleAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<List<ArtistID3>> sampleArtist = new MutableLiveData<>(null);
    private final MutableLiveData<List<Genre>> sampleGenres = new MutableLiveData<>(null);

    private String cachedMusicFolderId = Preferences.getActiveMusicFolderId();
    private int musicFolderGeneration = 0;

    public LibraryViewModel(@NonNull Application application) {
        super(application);

        directoryRepository = new DirectoryRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        genreRepository = new GenreRepository();
        playlistRepository = new PlaylistRepository();
    }

    /**
     * Same reasoning as HomeViewModel. Genres, playlists and the folder list take no folder id, so
     * they stay.
     */
    public boolean clearCacheIfMusicFolderChanged() {
        String activeMusicFolderId = Preferences.getActiveMusicFolderId();
        if (Objects.equals(activeMusicFolderId, cachedMusicFolderId)) return false;

        cachedMusicFolderId = activeMusicFolderId;
        musicFolderGeneration++;

        sampleAlbum.setValue(null);
        sampleArtist.setValue(null);

        return true;
    }

    /**
     * Same reasoning as HomeViewModel.reloadIfMusicFolderChanged.
     */
    public void reloadIfMusicFolderChanged(LifecycleOwner owner) {
        if (!clearCacheIfMusicFolderChanged()) return;

        refreshAlbumSample(owner);
        refreshArtistSample(owner);
    }

    /**
     * Same reasoning as HomeViewModel.setIfCurrentGeneration.
     */
    private <T> Observer<T> setIfCurrentGeneration(MutableLiveData<T> target) {
        int generation = musicFolderGeneration;

        return value -> {
            if (generation == musicFolderGeneration) target.setValue(value);
        };
    }

    public LiveData<List<MusicFolder>> getMusicFolders(LifecycleOwner owner) {
        if (musicFolders.getValue() == null) {
            directoryRepository.getMusicFolders().observe(owner, musicFolders::postValue);
        }

        return musicFolders;
    }

    public LiveData<Indexes> getIndexes(LifecycleOwner owner) {
        if (indexes.getValue() == null) {
            directoryRepository.getIndexes("0", null).observe(owner, indexes::postValue);
        }

        return indexes;
    }

    public LiveData<List<AlbumID3>> getAlbumSample(LifecycleOwner owner) {
        if (sampleAlbum.getValue() == null) {
            albumRepository.getAlbums("random", 10, null, null).observe(owner, setIfCurrentGeneration(sampleAlbum));
        }

        return sampleAlbum;
    }

    public LiveData<List<ArtistID3>> getArtistSample(LifecycleOwner owner) {
        if (sampleArtist.getValue() == null) {
            artistRepository.getArtists(true, 10).observe(owner, setIfCurrentGeneration(sampleArtist));
        }

        return sampleArtist;
    }

    public LiveData<List<Genre>> getGenreSample(LifecycleOwner owner) {
        if (sampleGenres.getValue() == null) {
            genreRepository.getGenres(true, 15).observe(owner, sampleGenres::postValue);
        }

        return sampleGenres;
    }

    public LiveData<List<Playlist>> getPlaylistSample(LifecycleOwner owner) {
        if (playlistSample.getValue() == null) {
            playlistRepository.getPlaylists(true, 10).observe(owner, playlistSample::postValue);
        }

        return playlistSample;
    }

    public void refreshAlbumSample(LifecycleOwner owner) {
        albumRepository.getAlbums("random", 10, null, null).observe(owner, setIfCurrentGeneration(sampleAlbum));
    }

    public void refreshArtistSample(LifecycleOwner owner) {
        artistRepository.getArtists(true, 10).observe(owner, setIfCurrentGeneration(sampleArtist));
    }

    public void refreshGenreSample(LifecycleOwner owner) {
        genreRepository.getGenres(true, 15).observe(owner, sampleGenres::postValue);
    }

    public void refreshPlaylistSample(LifecycleOwner owner) {
        playlistRepository.getPlaylists(true, 10).observe(owner, playlistSample::postValue);
    }
}
