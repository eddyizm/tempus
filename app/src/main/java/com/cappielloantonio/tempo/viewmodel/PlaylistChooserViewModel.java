package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.app.Dialog;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

public class PlaylistChooserViewModel extends AndroidViewModel {
    private final PlaylistRepository playlistRepository;
    private final MutableLiveData<List<Playlist>> playlists = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> playlistIsPublic = new MutableLiveData<>(false);

    public Boolean getIsPlaylistPublic() {
        return playlistIsPublic.getValue();
    }

    public void setIsPlaylistPublic(boolean isPublic) {
        playlistIsPublic.setValue(isPublic);
    }

    private ArrayList<Child> toAdd = new ArrayList<>();

    public PlaylistChooserViewModel(@NonNull Application application) {
        super(application);

        playlistRepository = new PlaylistRepository();
    }

    public LiveData<List<Playlist>> getPlaylistList(LifecycleOwner owner) {
        playlistRepository.getPlaylists(false, -1).observe(owner, playlists::postValue);
        return playlists;
    }

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
