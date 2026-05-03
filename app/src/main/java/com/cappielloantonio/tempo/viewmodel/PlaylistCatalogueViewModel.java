package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.List;

@UnstableApi
public class PlaylistCatalogueViewModel extends AndroidViewModel {
    private PlaylistRepository playlistRepository = new PlaylistRepository();

    private String type;

    private final MutableLiveData<String> sortOrder = new MutableLiveData<>(Constants.PLAYLIST_ORDER_BY_NAME);

    private final MutableLiveData<List<Playlist>> playlistList = new MutableLiveData<>(null);

    public PlaylistCatalogueViewModel(@NonNull Application application) {
        super(application);
        String currentPref = Preferences.getHomeSortPlaylists();
        sortOrder.setValue(currentPref);
        playlistRepository = new PlaylistRepository();
    }

    private final LiveData<List<Playlist>> sortedPlaylistList = Transformations.switchMap(sortOrder, order -> {
        return playlistRepository.getSortedPlaylists(order);
    });

    public LiveData<List<Playlist>> getPlaylistList(LifecycleOwner owner) {
        if (playlistList.getValue() == null) {
            playlistRepository.getPlaylists(false, -1).observe(owner, playlistList::postValue);
        }

        return playlistList;
    }

    public void setSortOrder(String order) {
        android.util.Log.d("TempusLog", "ViewModel setSortOrder called with: " + order);
        Preferences.setHomeSortPlaylists(order);
        sortOrder.setValue(order);
    }

    public LiveData<List<Playlist>> getSortedPlaylistList() {
        return sortedPlaylistList;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
