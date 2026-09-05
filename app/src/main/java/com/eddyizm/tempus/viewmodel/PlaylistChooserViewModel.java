package com.eddyizm.tempus.viewmodel;

import android.app.Application;
import android.app.Dialog;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.util.Preferences;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class PlaylistChooserViewModel extends AndroidViewModel {
    private final PlaylistRepository playlistRepository;
    // Null until the user touches the switch. A false default went out on the wire and changed
    // the visibility of a playlist the user only meant to add a song to.
    private final MutableLiveData<Boolean> playlistIsPublic = new MutableLiveData<>(null);

    public Boolean getIsPlaylistPublic() {
        return playlistIsPublic.getValue();
    }

    public void setIsPlaylistPublic(boolean isPublic) {
        playlistIsPublic.setValue(isPublic);
    }

    /**
     * Clears the visibility the user picked last time. This view model belongs to the activity, so
     * it outlives the dialog, while the switch is inflated unchecked every time. Without this the
     * two disagree and a later add sends a visibility the switch is not showing.
     */
    public void forgetVisibility() {
        playlistIsPublic.setValue(null);
    }

    private ArrayList<Child> toAdd = new ArrayList<>();

    public PlaylistChooserViewModel(@NonNull Application application) {
        super(application);

        playlistRepository = new PlaylistRepository();
    }

    @OptIn(markerClass = UnstableApi.class)
    public LiveData<List<Playlist>> getPlaylistList() {
        playlistRepository.refreshAllPlaylists();
        String sortOrder = Preferences.getHomeSortPlaylists();
        return playlistRepository.getSortedPlaylists(sortOrder);
    }

    @OptIn(markerClass = UnstableApi.class)
    public void addSongsToPlaylist(LifecycleOwner owner, Dialog dialog, String playlistId) {
        List<String> songIds = Lists.transform(toAdd, Child::getId);
        if (Preferences.allowPlaylistDuplicates()) {
            playlistRepository.addSongToPlaylist(playlistId, new ArrayList<>(songIds), getIsPlaylistPublic());
            dialog.dismiss();
        } else {
            playlistRepository.getPlaylistSongs(playlistId).observe(owner, playlistSongs -> {
                if (playlistSongs != null) {
                    List<String> playlistSongIds = Lists.transform(playlistSongs, Child::getId);
                    songIds.removeAll(playlistSongIds);
                }
                playlistRepository.addSongToPlaylist(playlistId, new ArrayList<>(songIds), getIsPlaylistPublic());
                dialog.dismiss();
            });
        }
    }

    public void setSongsToAdd(ArrayList<Child> songs) {
        toAdd = songs;
    }

    public ArrayList<Child> getSongsToAdd() {
        return toAdd;
    }
}
