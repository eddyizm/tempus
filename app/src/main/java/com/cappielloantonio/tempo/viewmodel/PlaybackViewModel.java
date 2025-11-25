package com.cappielloantonio.tempo.viewmodel;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cappielloantonio.tempo.ui.adapter.PlaybackAdapterInterface;

import java.util.Objects;

public class PlaybackViewModel extends ViewModel {
    public class State {
        public State() {}
        public State(String id, boolean isPlaying) {
            this.currentSongId = id;
            this.isPlaying = isPlaying;
        }
        public String currentSongId = null;
        public boolean isPlaying = false;
    }

    public final MutableLiveData<State> state = new MutableLiveData<>(new State());

    public void observePlayback(PlaybackAdapterInterface playbackAdapter, LifecycleOwner owner) {
        state.observe(owner, state -> {
            if (playbackAdapter != null) {
                boolean playing = state.isPlaying;
                playbackAdapter.setPlaybackState(state.currentSongId, playing);
            }
        });
    }

    public void reapplyPlayback(PlaybackAdapterInterface playbackAdapter) {
        if (playbackAdapter != null) {
            String id = state.getValue().currentSongId;
            boolean playing = state.getValue().isPlaying;
            playbackAdapter.setPlaybackState(id, playing);
        }
    }

    public void update(String songId, boolean playing) {
        State current = state.getValue();
        if (current == null || !Objects.equals(current.currentSongId, songId) || current.isPlaying != playing)
            state.postValue(new State(songId, playing));
    }

    public void clear() {
        state.postValue(new State());
    }
}