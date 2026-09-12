package com.eddyizm.tempus.util;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.subsonic.models.Child;

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
                // The songs live data has no initial value, so the first emission is the real
                // answer, and the observer is removed on it whatever the value.
                live.removeObserver(this);
                if (songs != null && !songs.isEmpty()) action.accept(songs);
            }
        };
        live.observe(owner, observer);
    }
}
