package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.R;
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
    private final MutableLiveData<Integer> saveResult = new MutableLiveData<>();
    private boolean savePending;

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

    /**
     * Renames without touching the contents. A null song list makes the repository skip the
     * replace, so a rename never resends the playlist.
     */
    @OptIn(markerClass = UnstableApi.class)
    public void renamePlaylist(String name, PlaylistRepository.PlaylistActionCallback callback) {
        playlistRepository.updatePlaylist(toEdit.getId(), name, null, callback);
    }

    @OptIn(markerClass = UnstableApi.class)
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

    /**
     * Edits the loaded list in place. The editor's adapter holds the same list, so nothing is
     * posted back, and a save reads the list as it stands.
     */
    public Child removeFromPlaylistSongLiveList(int position) {
        List<Child> songs = songLiveList.getValue();
        return Objects.requireNonNull(songs).remove(position);
    }

    /**
     * Puts a removed track back at its old index, clamped to the list size because a drag can move
     * rows while the undo is still on offer. Returns the index used, or minus one when there is no
     * list to put it in, so the caller can tell the adapter which row appeared.
     */
    public int restoreToPlaylistSongLiveList(int position, Child song) {
        List<Child> songs = songLiveList.getValue();
        if (songs == null || song == null) return -1;

        int at = Math.min(Math.max(position, 0), songs.size());
        songs.add(at, song);
        return at;
    }

    /**
     * Sends the loaded list under the playlist's current name. The outcome arrives through
     * {@link #getSaveResult()} instead of a callback.
     */
    @OptIn(markerClass = UnstableApi.class)
    public void saveTracks() {
        // One save at a time. Two in flight would race on the server, and the later list could
        // lose to the earlier one.
        if (savePending) return;
        savePending = true;
        updatePlaylist(toEdit.getName(), new PlaylistRepository.PlaylistActionCallback() {
            @Override
            public void onSuccess() {
                saveResult.postValue(R.string.playlist_editor_dialog_action_save_success);
            }

            @Override
            public void onFailure() {
                saveResult.postValue(R.string.playlist_editor_dialog_action_save_failure);
            }

            @Override
            public void onEmptied() {
                saveResult.postValue(R.string.playlist_editor_dialog_action_save_empty);
            }
        });
    }

    /**
     * True from the moment Save is tapped until the editor has shown the user its outcome.
     * The list is read once, synchronously, so any later edit is silently dropped, and the editor
     * is read only for as long as this holds. It is not "a save is running", the outcome is posted
     * to the looper, so there is a window where the request is done and the user has not been told
     * yet, and a tap in that window would edit a list that has already gone. It lives here and not
     * on the fragment, so a rotation mid save does not hand the list back.
     */
    public boolean isSavePending() {
        return savePending;
    }

    /** Called by the editor once it has shown the outcome, whichever it was. */
    public void clearSavePending() {
        savePending = false;
    }

    /** A string resource id naming the outcome of the last {@link #saveTracks()}, null once handled. */
    public LiveData<Integer> getSaveResult() {
        return saveResult;
    }

    public void clearSaveResult() {
        saveResult.setValue(null);
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
