package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.repository.SharingRepository;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.subsonic.models.Share;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PlaylistEditorViewModel extends AndroidViewModel {
    private static final String TAG = "PlaylistEditorViewModel";

    private final PlaylistRepository playlistRepository;
    private final SharingRepository sharingRepository;

    private ArrayList<Child> toAdd;
    private Playlist toEdit;

    private MutableLiveData<List<Child>> songLiveList = new MutableLiveData<>();

    private Integer loadedSongCount;
    private Observer<List<Child>> loadedCountObserver;

    public PlaylistEditorViewModel(@NonNull Application application) {
        super(application);

        playlistRepository = new PlaylistRepository();
        sharingRepository = new SharingRepository();
    }

    public void createPlaylist(String name, PlaylistRepository.PlaylistActionCallback callback) {
        playlistRepository.createPlaylist(null, name, new ArrayList(Lists.transform(toAdd, Child::getId)), callback);
    }

    public void updatePlaylist(String name, PlaylistRepository.PlaylistActionCallback callback) {
        ArrayList<String> songsId = getPlaylistSongIds();

        if (songsId == null) {
            if (callback != null) callback.onFailure();
            return;
        }

        if (songsId.isEmpty() && loadedSongCount != null && loadedSongCount > 0) {
            if (callback != null) callback.onEmptied();
            return;
        }

        playlistRepository.updatePlaylist(toEdit.getId(), name, songsId, callback);
    }

    public void deletePlaylist(PlaylistRepository.PlaylistActionCallback callback) {
        if (toEdit != null) playlistRepository.deletePlaylist(toEdit.getId(), callback);
    }

    public void setSongsToAdd(ArrayList<Child> songs) {
        toAdd = songs;
    }

    public ArrayList<Child> getSongsToAdd() {
        return toAdd;
    }

    public Playlist getPlaylistToEdit() {
        return toEdit;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setPlaylistToEdit(Playlist playlist) {
        this.toEdit = playlist;

        // Detach before reassigning, while songLiveList still points at the list being observed.
        detachLoadedCountObserver();
        loadedSongCount = null;

        if (playlist != null) {
            this.songLiveList = playlistRepository.getPlaylistSongs(toEdit.getId(), false);
            attachLoadedCountObserver();
        } else {
            this.songLiveList = new MutableLiveData<>();
        }
    }

    private void attachLoadedCountObserver() {
        loadedCountObserver = songs -> {
            if (songs == null) return;

            loadedSongCount = songs.size();
            detachLoadedCountObserver();
        };

        songLiveList.observeForever(loadedCountObserver);
    }

    private void detachLoadedCountObserver() {
        if (loadedCountObserver == null) return;

        songLiveList.removeObserver(loadedCountObserver);
        loadedCountObserver = null;
    }

    @Override
    protected void onCleared() {
        detachLoadedCountObserver();
    }

    public LiveData<List<Child>> getPlaylistSongLiveList() {
        return songLiveList;
    }

    public void removeFromPlaylistSongLiveList(int position) {
        List<Child> songs = songLiveList.getValue();
        Objects.requireNonNull(songs).remove(position);
        songLiveList.postValue(songs);
    }

    public void orderPlaylistSongLiveListAfterSwap(List<Child> songs) {
        songLiveList.postValue(songs);
    }

    /**
     * The ids of the tracks the user has arranged, or null when the list is not known. A null
     * list means the load has not finished or has failed, and a save is refused because the
     * editor is not showing the playlist. An empty list means nothing is on screen, which is a
     * removal only if the editor loaded tracks in the first place.
     */
    private ArrayList<String> getPlaylistSongIds() {
        List<Child> songs = songLiveList.getValue();

        if (songs == null) return null;

        ArrayList<String> ids = new ArrayList<>();

        for (Child song : songs) {
            ids.add(song.getId());
        }

        return ids;
    }

    public MutableLiveData<Share> sharePlaylist() {
        return sharingRepository.createShare(toEdit.getId(), toEdit.getName(), null);
    }
}
