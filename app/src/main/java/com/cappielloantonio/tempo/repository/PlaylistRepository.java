package com.cappielloantonio.tempo.repository;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.PlaylistDao;
import com.cappielloantonio.tempo.database.dao.PinnedPlaylistDao;
import com.cappielloantonio.tempo.model.PinnedPlaylist;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@UnstableApi
public class PlaylistRepository {
    private static final MutableLiveData<Boolean> playlistUpdateTrigger = new MutableLiveData<>();

    public LiveData<Boolean> getPlaylistUpdateTrigger() {
        return playlistUpdateTrigger;
    }

    public void notifyPlaylistChanged() {
        playlistUpdateTrigger.postValue(true);
        refreshAllPlaylists();
    }

    @androidx.media3.common.util.UnstableApi
    private final PinnedPlaylistDao pinnedPlaylistDao = AppDatabase.getInstance().pinnedPlaylistDao();
    private final PlaylistDao playlistDao = AppDatabase.getInstance().playlistDao();
    private static final MutableLiveData<List<Playlist>> allPlaylistsLiveData = new MutableLiveData<>();

    public LiveData<List<Playlist>> getAllPlaylists(LifecycleOwner owner) {
        refreshAllPlaylists();
        return allPlaylistsLiveData;
    }

    public void refreshAllPlaylists() {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();
                            allPlaylistsLiveData.postValue(playlists);
                            // cache all playlists
                            cacheAllPlaylists(playlists);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                    // OFFLINE MILESTONE: Future home of the "Server unreachable, falling back to cache" Toast
                    }
                });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void cacheAllPlaylists(List<Playlist> playlists) {
        new Thread(() -> {
            // clear table to prevert cross-server data bleed.
            playlistDao.deleteAll();
            // TODO add server aware join or column.
            playlistDao.insertAll(playlists);
            android.util.Log.d("PlaylistRepository", "Cached " + playlists.size() + " playlists to local DB.");
        }).start();
    }

    public MutableLiveData<List<Playlist>> getPlaylists(boolean random, int size) {
        MutableLiveData<List<Playlist>> listLivePlaylists = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();

                            cacheAllPlaylists(playlists);

                            if (random) {
                                Collections.shuffle(playlists);
                                listLivePlaylists.setValue(playlists.subList(0, Math.min(playlists.size(), size)));
                            } else {
                                listLivePlaylists.setValue(playlists);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                    }
                });

        return listLivePlaylists;
    }

    @androidx.media3.common.util.UnstableApi
    public LiveData<List<Playlist>> getSortedPlaylists(String sortOrder) {
        android.util.Log.d("TempusLog", "Repo reaching DAO with: " + sortOrder);
        return playlistDao.getSortedPlaylists(sortOrder);
    }

    public LiveData<List<Playlist>> getSortedPlaylistsPreview(String sortOrder, int limit) {
        return playlistDao.getSortedPlaylistsPreview(sortOrder, limit);
    }

    public MutableLiveData<List<Child>> getPlaylistSongs(String id) {
        MutableLiveData<List<Child>> listLivePlaylistSongs = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylist() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getPlaylist().getEntries();
                            listLivePlaylistSongs.setValue(songs);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                    }
                });

        return listLivePlaylistSongs;
    }

    public MutableLiveData<Playlist> getPlaylist(String id) {
        MutableLiveData<Playlist> playlistLiveData = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getPlaylist() != null) {
                            playlistLiveData.setValue(response.body().getSubsonicResponse().getPlaylist());
                        } else {
                            playlistLiveData.setValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        playlistLiveData.setValue(null);
                    }
                });

        return playlistLiveData;
    }

    public interface AddToPlaylistCallback {
        void onSuccess();
        void onFailure();
        void onAllSkipped();
    }

    public void addSongToPlaylist(String playlistId, ArrayList<String> songsId, Boolean playlistVisibilityIsPublic, AddToPlaylistCallback callback) {
        android.util.Log.d("PlaylistRepository", "addSongToPlaylist: id=" + playlistId + ", songs=" + songsId);
        if (songsId.isEmpty()) {
            if (callback != null) callback.onAllSkipped();
        } else{
            App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .updatePlaylist(playlistId, null, playlistVisibilityIsPublic, songsId, null)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful()) notifyPlaylistChanged();
                            if (callback != null) callback.onSuccess();
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            if (callback != null) callback.onFailure();
                        }
                    });
        }
    }

    public void removeSongFromPlaylist(String playlistId, int index, AddToPlaylistCallback callback) {
        ArrayList<Integer> indexes = new ArrayList<>();
        indexes.add(index);
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .updatePlaylist(playlistId, null, true, null, indexes)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) notifyPlaylistChanged();
                        if (callback != null) {
                            if (response.isSuccessful()) callback.onSuccess();
                            else callback.onFailure();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (callback != null) callback.onFailure();
                    }
                });
    }

    public void addSongToPlaylist(String playlistId, ArrayList<String> songsId, Boolean playlistVisibilityIsPublic) {
        addSongToPlaylist(playlistId, songsId, playlistVisibilityIsPublic, null);
    }

    public void createPlaylist(String playlistId, String name, ArrayList<String> songsId) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .createPlaylist(playlistId, name, songsId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) notifyPlaylistChanged();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public void updatePlaylist(String playlistId, String name, ArrayList<String> songsId) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .updatePlaylist(playlistId, name, true, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            // After renaming, we need to handle the song list update.
                            // Subsonic doesn't have a "replace all songs" in updatePlaylist.
                            // So we might still need to recreate if the songs changed significantly,
                            // but if we just renamed, we should update the local pinned database.
                            updateLocalPlaylistName(playlistId, name);
                            notifyPlaylistChanged();
                        }

                        // If songsId is provided, we might want to re-sync them.
                        // For now, let's at least fix the name duplication issue.
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                    }
                });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void updateLocalPlaylistName(String id, String newName) {
        new Thread(() -> {
            playlistDao.updateName(id, newName);
        }).start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void pin(String id) {
        new Thread(() -> {
            pinnedPlaylistDao.pin(id);
        }).start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void unpin(String id) {
        new Thread(() -> {
            pinnedPlaylistDao.unpin(id);
        }).start();
    }

    public void deletePlaylist(String playlistId) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .deletePlaylist(playlistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) notifyPlaylistChanged();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }
    @androidx.media3.common.util.UnstableApi
    public LiveData<List<PinnedPlaylist>> getPinnedPlaylists() {
        return pinnedPlaylistDao.getAllPinnedIds();
    }

    @androidx.media3.common.util.UnstableApi
    public void insert(Playlist playlist) {
        InsertThreadSafe insert = new InsertThreadSafe(playlistDao, playlist);
        Thread thread = new Thread(insert);
        thread.start();
    }

    @androidx.media3.common.util.UnstableApi
    public void delete(Playlist playlist) {
        DeleteThreadSafe delete = new DeleteThreadSafe(playlistDao, playlist);
        Thread thread = new Thread(delete);
        thread.start();
    }

    @androidx.media3.common.util.UnstableApi
    public void updatePinnedPlaylists() {
        updatePinnedPlaylists(null);
    }

    @androidx.media3.common.util.UnstableApi
    public void updatePinnedPlaylists(List<String> forceIds) {
        new Thread(() -> {
            List<Playlist> pinned = playlistDao.getAllSync();
            if (pinned != null && !pinned.isEmpty()) {
                App.getSubsonicClientInstance(false)
                        .getPlaylistClient()
                        .getPlaylists()
                        .enqueue(new Callback<ApiResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null) {
                                    List<Playlist> remotes = response.body().getSubsonicResponse().getPlaylists().getPlaylists();
                                    new Thread(() -> {
                                        for (Playlist p : pinned) {
                                            for (Playlist r : remotes) {
                                                if (p.getId().equals(r.getId())) {
                                                    p.setName(r.getName());
                                                    p.setSongCount(r.getSongCount());
                                                    p.setDuration(r.getDuration());
                                                    p.setCoverArtId(r.getCoverArtId());
                                                    playlistDao.insert(p);
                                                    break;
                                                }
                                            }
                                        }
                                    }).start();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            }
                        });
            }
        }).start();
    }

    private static class InsertThreadSafe implements Runnable {
        private final PlaylistDao playlistDao;
        private final Playlist playlist;

        public InsertThreadSafe(PlaylistDao playlistDao, Playlist playlist) {
            this.playlistDao = playlistDao;
            this.playlist = playlist;
        }

        @Override
        public void run() {
            playlistDao.insert(playlist);
        }
    }

    private static class DeleteThreadSafe implements Runnable {
        private final PlaylistDao playlistDao;
        private final Playlist playlist;

        public DeleteThreadSafe(PlaylistDao playlistDao, Playlist playlist) {
            this.playlistDao = playlistDao;
            this.playlist = playlist;
        }

        @Override
        public void run() {
            playlistDao.delete(playlist);
        }
    }
}
