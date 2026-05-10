package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.model.PinnedPlaylist;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;

import java.util.List;

@UnstableApi
public class PlaylistPageViewModel extends AndroidViewModel {
    private final PlaylistRepository playlistRepository;

    private Playlist playlist;
    private boolean isOffline;

    private final MutableLiveData<List<Child>> songLiveList = new MutableLiveData<>();

    public PlaylistPageViewModel(@NonNull Application application) {
        super(application);

        playlistRepository = new PlaylistRepository();
        playlistRepository.getPlaylistUpdateTrigger().observeForever(needsRefresh -> {
            if (needsRefresh != null && needsRefresh && playlist != null) {
                refreshSongs();
            }
        });
    }

    public LiveData<List<Child>> getPlaylistSongLiveList() {
        if (songLiveList.getValue() == null && playlist != null) {
            refreshSongs();
        }
        return songLiveList;
    }

    private void refreshSongs() {
        if (playlist == null) return;
        LiveData<List<Child>> remoteData = playlistRepository.getPlaylistSongs(playlist.getId());
        remoteData.observeForever(new androidx.lifecycle.Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                songLiveList.postValue(songs);
                remoteData.removeObserver(this);
            }
        });
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        if (this.playlist == null || !this.playlist.getId().equals(playlist.getId())) {
            this.playlist = playlist;
            this.songLiveList.setValue(null); // Clear old data immediately
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public LiveData<Boolean> isPinned(LifecycleOwner owner) {
        MutableLiveData<Boolean> isPinnedLive = new MutableLiveData<>();

        playlistRepository.getPinnedPlaylists().observe(owner, playlists -> {
            isPinnedLive.postValue(playlists.stream().anyMatch(obj -> obj.getPlaylistId().equals(playlist.getId())));
        });

        return isPinnedLive;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setPinned(boolean isNowPinned) {
        playlistRepository.insert(playlist);

        if (isNowPinned) {
            playlistRepository.pin(playlist.getId());
        } else {
            playlistRepository.unpin(playlist.getId());
        }
    }
}
