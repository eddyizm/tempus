package com.cappielloantonio.tempo.util;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;

import java.util.List;
import java.util.function.Consumer;

public final class LiveDataUtils {
    private LiveDataUtils() {}

    // Default convenience method that constructs a PlaylistRepository
    public static void observePlaylistSongsOnce(@NonNull LifecycleOwner owner, @NonNull String playlistId, @NonNull Consumer<List<Child>> action) {
        observePlaylistSongsOnce(new PlaylistRepository(), owner, playlistId, action);
    }

    // Testable overload that accepts a PlaylistRepository instance
    public static void observePlaylistSongsOnce(@NonNull PlaylistRepository repo, @NonNull LifecycleOwner owner, @NonNull String playlistId, @NonNull Consumer<List<Child>> action) {
        final LiveData<List<Child>> live = repo.getPlaylistSongs(playlistId);
        final Observer<List<Child>> observer = new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                if (songs != null && !songs.isEmpty()) {
                    action.accept(songs);
                    live.removeObserver(this);
                }
            }
        };
        live.observe(owner, observer);
    }
}
