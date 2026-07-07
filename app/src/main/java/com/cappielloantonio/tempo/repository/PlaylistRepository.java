package com.cappielloantonio.tempo.repository;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.PlaylistDao;
import com.cappielloantonio.tempo.database.dao.PinnedPlaylistDao;
import com.cappielloantonio.tempo.database.dao.PlaylistSongDao;
import com.cappielloantonio.tempo.model.PinnedPlaylist;
import com.cappielloantonio.tempo.model.PlaylistSong;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private void handleMissingPlaylist(String id, Runnable onMissing) {
        new Thread(() -> {
            // Must delete dependent records first to avoid foreign key constraint violations
            playlistSongDao.deleteForPlaylist(id);
            pinnedPlaylistDao.unpin(id);
            playlistDao.deleteById(id);
            
            if (onMissing != null) {
                new Handler(Looper.getMainLooper()).post(onMissing);
            }
            refreshAllPlaylists();
        }).start();
    }

    @androidx.media3.common.util.UnstableApi
    private final PinnedPlaylistDao pinnedPlaylistDao = AppDatabase.getInstance().pinnedPlaylistDao();
    private final PlaylistDao playlistDao = AppDatabase.getInstance().playlistDao();
    private final PlaylistSongDao playlistSongDao = AppDatabase.getInstance().playlistSongDao();
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
            // Remove playlists from DB that are not in the new list
            List<Playlist> cachedPlaylists = playlistDao.getAllSync();
            if (cachedPlaylists != null) {
                Set<String> remoteIds = new HashSet<>();
                for (Playlist remote : playlists) {
                    remoteIds.add(remote.getId());
                }

                for (Playlist cached : cachedPlaylists) {
                    if (!remoteIds.contains(cached.getId())) {
                        playlistSongDao.deleteForPlaylist(cached.getId());
                        pinnedPlaylistDao.unpin(cached.getId());
                        playlistDao.delete(cached);
                        android.util.Log.d("PlaylistRepository", "Removed orphaned playlist " + cached.getId() + " from local DB.");
                    }
                }
            }
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
                        if (response.isSuccessful() && response.body() != null) {
                            SubsonicResponse sr = response.body().getSubsonicResponse();
                            if (sr.getPlaylist() != null) {
                                List<Child> songs = sr.getPlaylist().getEntries();
                                if (songs == null) {
                                    songs = new ArrayList<>();
                                }
                                listLivePlaylistSongs.setValue(songs);
                                cachePlaylistSongs(sr.getPlaylist(), songs);
                            } else if (sr.getError() != null && sr.getError().getCode() != null && sr.getError().getCode() == 70) {
                                // Subsonic Standard Error Code 70: The requested data was not found.
                                handleMissingPlaylist(id, null);
                                listLivePlaylistSongs.setValue(null);
                            } else {
                                listLivePlaylistSongs.setValue(null);
                            }
                        } else {
                            listLivePlaylistSongs.setValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        fetchCachedPlaylistSongs(id, listLivePlaylistSongs);
                    }
                });

        return listLivePlaylistSongs;
    }

    private void cachePlaylistSongs(Playlist playlist, List<Child> songs) {
        new Thread(() -> {
            String playlistId = playlist.getId();
            // playlist_song has a foreign key to playlist.id, so the playlist row must
            // exist before its songs are inserted. The full-list path caches playlists via
            // cacheAllPlaylists, but the single-playlist endpoint (used by deep links) does
            // not — without this the songs insert crashes with a FOREIGN KEY constraint.
            // Insert before the early-return dedup checks so the row is ensured even when
            // the songs are already cached. See issue #729.
            playlistDao.insertIfAbsent(playlist);
            List<PlaylistSong> cached = playlistSongDao.getSongsForPlaylistSync(playlistId);
            if (songs == null || songs.isEmpty()) {
                if (cached != null && !cached.isEmpty()) {
                    playlistSongDao.deleteForPlaylist(playlistId);
                    android.util.Log.d("PlaylistRepository", "Cleared songs for playlist " + playlistId);
                }
                return;
            }

            // Simple check: compare sizes
            if (cached != null && cached.size() == songs.size()) {
                // If sizes match, assume no change for now to avoid expensive deep equality check
                // TODO it would be better to check update/create dates if possible then do actual song id comparison
                android.util.Log.d("PlaylistRepository", "Songs for playlist " + playlistId + " already cached (size match).");
                return;
            }

            List<PlaylistSong> playlistSongs = new ArrayList<>();
            for (Child child : songs) {
                playlistSongs.add(new PlaylistSong(playlistId, child));
            }
            playlistSongDao.deleteForPlaylist(playlistId);
            playlistSongDao.insertAll(playlistSongs);
            android.util.Log.d("PlaylistRepository", "Cached " + playlistSongs.size() + " songs for playlist " + playlistId);
        }).start();
    }

    private void fetchCachedPlaylistSongs(String playlistId, MutableLiveData<List<Child>> liveData) {
        new Thread(() -> {
            List<PlaylistSong> cached = playlistSongDao.getSongsForPlaylistSync(playlistId);
            if (cached != null && !cached.isEmpty()) {
                List<Child> songs = new ArrayList<>();
                for (PlaylistSong ps : cached) {
                    Child child = new Child(ps.getId());
                    child.setTitle(ps.getTitle());
                    child.setArtist(ps.getArtist());
                    child.setAlbum(ps.getAlbum());
                    child.setTrack(ps.getTrack());
                    child.setCoverArtId(ps.getCoverArtId());
                    child.setDuration(ps.getDuration());
                    child.setAlbumId(ps.getAlbumId());
                    child.setArtistId(ps.getArtistId());
                    songs.add(child);
                }
                liveData.postValue(songs);
            }
        }).start();
    }

    public MutableLiveData<Playlist> getPlaylist(String id) {
        MutableLiveData<Playlist> playlistLiveData = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            SubsonicResponse sr = response.body().getSubsonicResponse();
                            if (sr.getPlaylist() != null) {
                                playlistLiveData.setValue(sr.getPlaylist());
                            } else if (sr.getError() != null && sr.getError().getCode() != null && sr.getError().getCode() == 70) {
                                // Subsonic Standard Error Code 70: The requested data was not found.
                                handleMissingPlaylist(id, null);
                                playlistLiveData.setValue(null);
                            } else {
                                playlistLiveData.setValue(null);
                            }
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

    public void createPlaylist(String playlistId, String name, ArrayList<String> songsId, PlaylistActionCallback callback) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .createPlaylist(playlistId, name, songsId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            notifyPlaylistChanged();
                            if (callback != null) callback.onSuccess();
                        } else {
                            if (callback != null) callback.onFailure();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (callback != null) callback.onFailure();
                    }
                });
    }

    public void updatePlaylist(String playlistId, String name, ArrayList<String> songsId, PlaylistActionCallback callback) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .updatePlaylist(playlistId, name, true, songsId, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            updateLocalPlaylistName(playlistId, name);
                            notifyPlaylistChanged();
                            if (callback != null) callback.onSuccess();
                        } else {
                            if (callback != null) callback.onFailure();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (callback != null) callback.onFailure();
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

    public interface PlaylistActionCallback {
        void onSuccess();
        void onFailure();
    }

    public void deletePlaylist(String playlistId, PlaylistActionCallback callback) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .deletePlaylist(playlistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            new Thread(() -> {
                                playlistSongDao.deleteForPlaylist(playlistId);
                                playlistDao.deleteById(playlistId);
                                android.util.Log.d("PlaylistRepository", "Deleted playlist " + playlistId + " and its songs from local DB.");
                            }).start();
                            notifyPlaylistChanged();
                            if (callback != null) callback.onSuccess();
                        } else {
                            if (callback != null) callback.onFailure();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (callback != null) callback.onFailure();
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
