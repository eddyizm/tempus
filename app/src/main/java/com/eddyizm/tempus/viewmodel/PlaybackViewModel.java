package com.eddyizm.tempus.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Objects;

public class PlaybackViewModel extends ViewModel {

    private final MutableLiveData<String> currentSongId = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> currentMediaItemIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);

    public LiveData<String> getCurrentSongId() {
        return currentSongId;
    }

    public LiveData<Integer> getCurrentMediaItemIndex() {
        return currentMediaItemIndex;
    }

    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }

    public void update(String songId, boolean playing) {
        update(songId, -1, playing);
    }

    public void update(String songId, int mediaItemIndex, boolean playing) {
        if (!Objects.equals(currentSongId.getValue(), songId)) {
            currentSongId.postValue(songId);
        }
        currentMediaItemIndex.postValue(mediaItemIndex);
        if (!Objects.equals(isPlaying.getValue(), playing)) {
            isPlaying.postValue(playing);
        }
    }

    public void clear() {
        currentSongId.postValue(null);
        currentMediaItemIndex.postValue(-1);
        isPlaying.postValue(false);
    }
}