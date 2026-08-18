package com.cappielloantonio.tempo.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

public class PlaybackViewModel extends ViewModel {

    private final MutableLiveData<String> currentSongId = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);

    public LiveData<String> getCurrentSongId() {
        return currentSongId;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public void update(String songId, boolean playing) {
        if (!Objects.equals(currentSongId.getValue(), songId)) {
            currentSongId.postValue(songId);
        }
        if (!Objects.equals(isPlaying.getValue(), playing)) {
            isPlaying.postValue(playing);
        }
    }

    public void clear() {
        currentSongId.postValue(null);
        isPlaying.postValue(false);
    }
}