package com.eddyizm.tempus.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.PlaylistDao;
import com.eddyizm.tempus.database.dao.PendingPlaylistEditDao;
import com.eddyizm.tempus.database.dao.PinnedPlaylistDao;
import com.eddyizm.tempus.database.dao.PlaylistSnapshotDao;
import com.eddyizm.tempus.database.dao.PlaylistSongDao;
import com.eddyizm.tempus.database.dao.SyncedPlaylistDao;
import com.eddyizm.tempus.model.PendingPlaylistEdit;
import com.eddyizm.tempus.model.PinnedPlaylist;
import com.eddyizm.tempus.model.PlaylistSnapshot;
import com.eddyizm.tempus.model.PlaylistSong;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.subsonic.models.ResponseStatus;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;
import com.eddyizm.tempus.util.DownloadRepair;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.OfflinePlaylistOrder;
import com.eddyizm.tempus.util.PendingEditOps;
import com.eddyizm.tempus.util.Preferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
            String server = Preferences.getServer();
            if (server != null) syncedPlaylistDao.remove(id, server);
            forgetOrderAndEdits(id);
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
    private final SyncedPlaylistDao syncedPlaylistDao = AppDatabase.getInstance().syncedPlaylistDao();
    private final PendingPlaylistEditDao pendingPlaylistEditDao = AppDatabase.getInstance().pendingPlaylistEditDao();
    private final PlaylistSnapshotDao playlistSnapshotDao = AppDatabase.getInstance().playlistSnapshotDao();
    private static final java.util.concurrent.ExecutorService SYNCED_WRITES = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static volatile String keptSyncRanFor;
    private static final MutableLiveData<List<Playlist>> allPlaylistsLiveData = new MutableLiveData<>();
    private final PendingPlaylistEditRepository pendingEdits = new PendingPlaylistEditRepository();

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
                            if (playlists == null) {
                                playlists = new ArrayList<>();
                            }
                            allPlaylistsLiveData.postValue(playlists);
                            // cache all playlists
                            cacheAllPlaylists(playlists);
                            // The server answers, so edits kept on the phone can go out.
                            PlaylistFlush.kickThrottled();
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

                String server = Preferences.getServer();
                for (Playlist cached : cachedPlaylists) {
                    if (!remoteIds.contains(cached.getId())) {
                        playlistSongDao.deleteForPlaylist(cached.getId());
                        pinnedPlaylistDao.unpin(cached.getId());
                        if (server != null) syncedPlaylistDao.remove(cached.getId(), server);
                        forgetOrderAndEdits(cached.getId());
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
        // No initial value. An empty list delivered before the request answers makes a caller that
        // hides a section on an empty list blank it on every refresh, and leaves it blank for good
        // when the request fails, because neither failure path below posts anything.
        MutableLiveData<List<Playlist>> listLivePlaylists = new MutableLiveData<>();

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
        return getPlaylistSongs(id, true);
    }

    /**
     * @param allowCache whether a locally cached copy may stand in when the server cannot be
     *                   reached. A caller that writes the list back must pass false, the cache is a
     *                   snapshot, and saving it would delete whatever has been added since it was
     *                   written.
     */
    public MutableLiveData<List<Child>> getPlaylistSongs(String id, boolean allowCache) {
        return getPlaylistSongs(id, allowCache, null);
    }

    /**
     * @param onMissing run on the main thread when the server says the playlist does not exist,
     *                  after its local rows are gone; that case publishes null as well, before
     *                  the runnable runs. Every other failure publishes null, except a transport
     *                  failure with allowCache, which publishes the cached list, or nothing when
     *                  there is none.
     */
    public MutableLiveData<List<Child>> getPlaylistSongs(String id, boolean allowCache, Runnable onMissing) {
        return getPlaylistSongs(id, allowCache ? CacheFallback.ANY : CacheFallback.NONE, onMissing);
    }

    /**
     * A playlist loaded to be edited and written back. fromPhone says the list came from this
     * phone, its unsynced edit or the server's last known order, not from the server just now, and
     * loadedIds keeps the order as loaded, since the editor edits songs in place.
     */
    public static final class EditableSongs {
        public final List<Child> songs;
        public final boolean fromPhone;
        private final List<String> loadedIds;

        EditableSongs(List<Child> songs, boolean fromPhone) {
            this.songs = songs;
            this.fromPhone = fromPhone;
            this.loadedIds = idsOf(songs);
        }

        List<String> ids() {
            return loadedIds;
        }
    }

    /**
     * For a caller that writes the list back. While the server answers this is the server's list,
     * and when it cannot be reached it is the cached list, but only when the server's order for it
     * is known, since an edit made on top of it is merged against that order later. Null when
     * there is nothing to edit.
     */
    public MutableLiveData<EditableSongs> getPlaylistSongsForEdit(String id, Runnable onMissing) {
        MutableLiveData<EditableSongs> editable = new MutableLiveData<>();
        loadPlaylistSongs(id, CacheFallback.ORDERED, onMissing, (songs, fromPhone) -> publish(editable, songs != null ? new EditableSongs(songs, fromPhone) : null));
        return editable;
    }

    private enum CacheFallback { NONE, ANY, ORDERED }

    private interface SongsSink {
        void accept(List<Child> songs, boolean fromPhone);
    }

    private MutableLiveData<List<Child>> getPlaylistSongs(String id, CacheFallback fallback, Runnable onMissing) {
        MutableLiveData<List<Child>> listLivePlaylistSongs = new MutableLiveData<>();
        loadPlaylistSongs(id, fallback, onMissing, (songs, fromPhone) -> publish(listLivePlaylistSongs, songs));
        return listLivePlaylistSongs;
    }

    // Main thread callers keep the synchronous setValue the server answer always had.
    private static <T> void publish(MutableLiveData<T> live, T value) {
        if (Looper.myLooper() == Looper.getMainLooper()) live.setValue(value);
        else live.postValue(value);
    }

    /**
     * A playlist with changes made on this phone that are not synced yet is answered from the
     * cache, which holds those changes. The server's list would not show them, and an index into
     * it would point at the wrong track once the changes are merged.
     */
    private void loadPlaylistSongs(String id, CacheFallback fallback, Runnable onMissing, SongsSink sink) {
        new Thread(() -> {
            if (pendingEdits.hasRow(id)) {
                sink.accept(cachedPlaylistSongs(id), true);
                return;
            }
            fetchPlaylistSongs(id, fallback, onMissing, sink);
        }).start();
    }

    private void fetchPlaylistSongs(String id, CacheFallback fallback, Runnable onMissing, SongsSink sink) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        // Without a network the HTTP cache answers. A stored copy is no base to
                        // edit on, and its made-up 504 should not read as the playlist failing.
                        if (!reachedServer(response) && (fallback == CacheFallback.ORDERED || !response.isSuccessful())) {
                            onFailure(call, new IOException("Answered from the cache"));
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null) {
                            SubsonicResponse sr = response.body().getSubsonicResponse();
                            if (sr.getPlaylist() != null) {
                                List<Child> songs = sr.getPlaylist().getEntries();
                                if (songs == null) {
                                    songs = new ArrayList<>();
                                }
                                sink.accept(songs, false);
                                // The cache thread iterates its list while the editor may be
                                // removing from the one just published, so it gets a copy.
                                cachePlaylistSongs(sr.getPlaylist(), new ArrayList<>(songs));
                            } else if (sr.getError() != null && sr.getError().getCode() != null && sr.getError().getCode() == 70) {
                                // Subsonic Standard Error Code 70: The requested data was not found.
                                handleMissingPlaylist(id, onMissing);
                                sink.accept(null, false);
                            } else {
                                sink.accept(null, false);
                            }
                        } else {
                            sink.accept(null, false);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        if (fallback == CacheFallback.ANY) {
                            new Thread(() -> {
                                List<Child> songs = cachedPlaylistSongs(id);
                                if (songs != null && !songs.isEmpty()) sink.accept(songs, true);
                            }).start();
                        } else if (fallback == CacheFallback.ORDERED) {
                            new Thread(() -> sink.accept(cachedOrder(id) != null ? cachedPlaylistSongs(id) : null, true)).start();
                        } else {
                            sink.accept(null, false);
                        }
                    }
                });
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
            // insertIfAbsent leaves an existing row alone, so this response's visibility would be
            // thrown away whenever the row already exists. The write paths read this column, so
            // the value this response carried is written back on its own.
            playlistDao.updateVisibility(playlistId, playlist.isUniversal());
            // Written on every read, before the size check below can skip the song rows, since a
            // reorder keeps the size and the offline editor needs the order.
            String server = Preferences.getServer();
            if (server != null) playlistSnapshotDao.upsert(new PlaylistSnapshot(playlistId, server, idsOf(songs)));
            List<PlaylistSong> cached = playlistSongDao.getSongsForPlaylistSync(playlistId);
            if (songs == null || songs.isEmpty()) {
                if (cached != null && !cached.isEmpty()) {
                    playlistSongDao.deleteForPlaylist(playlistId);
                    android.util.Log.d("PlaylistRepository", "Cleared songs for playlist " + playlistId);
                }
                return;
            }

            // The offline view shows tracks from these rows, so a track swapped for another has to
            // land here even when the count stays the same.
            if (sameSongIds(cached, songs)) {
                android.util.Log.d("PlaylistRepository", "Songs for playlist " + playlistId + " already cached.");
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

    /**
     * The playlist as this phone knows it, from the cache: the pending edit's list when there is
     * one, otherwise the server's last known order, otherwise the cached rows in whatever order
     * the table returns them. Null when nothing is cached. Touches the database, so callers have
     * to be off the main thread.
     */
    List<Child> cachedPlaylistSongs(String playlistId) {
        List<String> order = cachedOrder(playlistId);
        List<PlaylistSong> cached = playlistSongDao.getSongsForPlaylistSync(playlistId);
        if (cached == null) cached = new ArrayList<>();

        if (order != null) {
            Map<String, PlaylistSong> byId = new HashMap<>();
            for (PlaylistSong song : cached) byId.put(song.getId(), song);
            return OfflinePlaylistOrder.order(order, byId);
        }

        if (cached.isEmpty()) return null;
        List<Child> songs = new ArrayList<>();
        for (PlaylistSong song : cached) songs.add(OfflinePlaylistOrder.toChild(song));
        return songs;
    }

    private List<String> cachedOrder(String playlistId) {
        String server = Preferences.getServer();
        if (server == null) return null;

        PendingPlaylistEdit pending = pendingPlaylistEditDao.get(playlistId, server);
        if (pending != null) return pending.getLocalIds();

        PlaylistSnapshot snapshot = playlistSnapshotDao.get(playlistId, server);
        return snapshot != null ? snapshot.getSongIds() : null;
    }

    // Only for the server in use, the playlist table is shared by every server.
    private void forgetOrderAndEdits(String playlistId) {
        String server = Preferences.getServer();
        if (server == null) return;
        playlistSnapshotDao.remove(playlistId, server);
        pendingPlaylistEditDao.remove(playlistId, server);
    }

    static ArrayList<String> idsOf(List<Child> songs) {
        ArrayList<String> ids = new ArrayList<>();
        if (songs == null) return ids;
        for (Child song : songs) ids.add(song.getId());
        return ids;
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

        /** The change was kept on this phone, to be synced when the server can be reached. */
        default void onQueued() {
            onSuccess();
        }
    }

    /**
     * @param playlistVisibilityIsPublic true when the user marked the playlist public with the
     *                                   chooser switch. Anything else falls back to the playlist's
     *                                   cached visibility. The switch marks a playlist public and
     *                                   never sends false, because false is not a safe value to
     *                                   send.
     */
    public void addSongToPlaylist(String playlistId, ArrayList<String> songsId, Boolean playlistVisibilityIsPublic, AddToPlaylistCallback callback) {
        addSongToPlaylist(playlistId, songsId, null, playlistVisibilityIsPublic, callback);
    }

    /** @param songs details of the added tracks, kept in the cache when the add has to wait. */
    public void addSongToPlaylist(String playlistId, ArrayList<String> songsId, List<Child> songs, Boolean playlistVisibilityIsPublic, AddToPlaylistCallback callback) {
        android.util.Log.d("PlaylistRepository", "addSongToPlaylist: id=" + playlistId + ", songs=" + songsId);
        if (songsId.isEmpty()) {
            if (callback != null) callback.onAllSkipped();
            return;
        }
        new Thread(() -> {
            if (pendingEdits.hasRow(playlistId)) {
                queueAdd(playlistId, songsId, songs, callback);
                return;
            }
            Boolean isPublic = Boolean.TRUE.equals(playlistVisibilityIsPublic)
                    ? Boolean.TRUE
                    : visibilityToSend(playlistId);
            App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .updatePlaylist(playlistId, null, isPublic, songsId, null)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (!reachedServer(response)) {
                                onFailure(call, new IOException("Answered from the cache"));
                                return;
                            }
                            if (isAccepted(response)) notifyPlaylistChanged();
                            if (callback != null) {
                                if (isAccepted(response)) callback.onSuccess();
                                else callback.onFailure();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            queueAdd(playlistId, songsId, songs, callback);
                        }
                    });
        }).start();
    }

    private void queueAdd(String playlistId, List<String> songsId, List<Child> songs, AddToPlaylistCallback callback) {
        queue(playlistId, current -> PendingEditOps.append(current, songsId), null, songs, null,
                callback != null ? callback::onQueued : null,
                callback != null ? callback::onFailure : null,
                callback != null ? callback::onFailure : null);
    }

    /**
     * The visibility to send with a write, or null to send none at all. True only when the server
     * has said this playlist is public.
     * <p>
     * Servers split two ways on an absent public parameter. One group reads the parameter's value,
     * so leaving it out leaves the visibility alone. The other never acts on what the value says,
     * making the playlist public whenever the parameter arrives and private when it does not. So
     * false is never sent, and true is sent whenever the playlist is known to be public, since on
     * the second group leaving it out would make that playlist private. A literal true published
     * every private playlist that was edited, which is what this exists to stop.
     * <p>
     * The value is the one the server last reported, held in the cached playlist row, which the
     * full playlist list refreshes on every load. Reading it here keeps the screens out of it and
     * costs no extra request. It touches the database, so callers have to be off the main thread.
     */
    Boolean visibilityToSend(String playlistId) {
        return Boolean.TRUE.equals(playlistDao.isPlaylistPublic(playlistId)) ? Boolean.TRUE : null;
    }

    public void removeSongFromPlaylist(String playlistId, int index, AddToPlaylistCallback callback) {
        removeSongFromPlaylist(playlistId, index, null, callback);
    }

    /**
     * @param songId the track the page showed at index. When the remove has to wait on the phone,
     *               the phone's list can differ from the page's, so the track is found by id.
     */
    public void removeSongFromPlaylist(String playlistId, int index, String songId, AddToPlaylistCallback callback) {
        ArrayList<Integer> indexes = new ArrayList<>();
        indexes.add(index);
        new Thread(() -> {
            if (pendingEdits.hasRow(playlistId)) {
                queueRemove(playlistId, index, songId, callback);
                return;
            }
            App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .updatePlaylist(playlistId, null, visibilityToSend(playlistId), null, indexes)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (!reachedServer(response)) {
                                onFailure(call, new IOException("Answered from the cache"));
                                return;
                            }
                            if (isAccepted(response)) notifyPlaylistChanged();
                            if (callback != null) {
                                if (isAccepted(response)) callback.onSuccess();
                                else callback.onFailure();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            queueRemove(playlistId, index, songId, callback);
                        }
                    });
        }).start();
    }

    private void queueRemove(String playlistId, int index, String songId, AddToPlaylistCallback callback) {
        queue(playlistId, current -> {
                    if (songId != null) return PendingEditOps.removeAt(current, index, songId);
                    if (index < 0 || index >= current.size()) return null;
                    List<String> next = new ArrayList<>(current);
                    next.remove(index);
                    return next;
                }, null, null, null,
                callback != null ? callback::onQueued : null,
                callback != null ? callback::onFailure : null,
                callback != null ? callback::onFailure : null);
    }

    public void addSongToPlaylist(String playlistId, ArrayList<String> songsId, Boolean playlistVisibilityIsPublic) {
        addSongToPlaylist(playlistId, songsId, playlistVisibilityIsPublic, null);
    }

    /**
     * With a playlistId this replaces that playlist's tracks, and such a replace waits on the phone
     * like any other edit when the server cannot be reached. A new playlist is never made offline.
     */
    public void createPlaylist(String playlistId, String name, ArrayList<String> songsId, PlaylistActionCallback callback) {
        if (playlistId == null) {
            App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .createPlaylist(null, name, songsId)
                    .enqueue(resultHandler(callback));
            return;
        }
        new Thread(() -> {
            if (pendingEdits.hasRow(playlistId)) {
                queueReplace(playlistId, songsId, null, name, null, callback);
                return;
            }
            App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .createPlaylist(playlistId, name, songsId)
                    .enqueue(replaceHandler(playlistId, songsId, null, name, callback));
        }).start();
    }

    public void updatePlaylist(String playlistId, String name, ArrayList<String> songsId, PlaylistActionCallback callback) {
        updatePlaylist(playlistId, name, songsId, null, callback);
    }

    /**
     * @param songsId the full list to write, or null to rename only.
     * @param songs   details of the tracks, kept in the cache when the save has to wait.
     */
    public void updatePlaylist(String playlistId, String name, ArrayList<String> songsId, List<Child> songs, PlaylistActionCallback callback) {
        new Thread(() -> {
            if (pendingEdits.hasRow(playlistId)) {
                queueReplace(playlistId, songsId, songs, name, null, callback);
                return;
            }
            App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .updatePlaylist(playlistId, name, visibilityToSend(playlistId), null, null)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (!reachedServer(response)) {
                                onFailure(call, new IOException("Answered from the cache"));
                                return;
                            }
                            if (isAccepted(response)) {
                                updateLocalPlaylistName(playlistId, name);
                                replacePlaylistSongs(playlistId, songsId, songs, callback);
                            } else {
                                if (callback != null) callback.onFailure();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            queueReplace(playlistId, songsId, songs, name, null, callback);
                        }
                    });
        }).start();
    }

    /**
     * Saves a list the user edited on top of loaded. A list that came from the phone, not from the
     * server, always waits on the phone with the loaded list as its base, even when the server can
     * be reached by now, because writing it straight back would drop whatever the server gained
     * since. The sync then merges it onto the server's list.
     */
    public void saveEditedPlaylist(String playlistId, String name, ArrayList<String> songsId, List<Child> songs, EditableSongs loaded, PlaylistActionCallback callback) {
        if (loaded != null && loaded.fromPhone) {
            queueReplace(playlistId, songsId, songs, name, loaded.ids(), callback);
        } else {
            updatePlaylist(playlistId, name, songsId, songs, callback);
        }
    }

    /** As {@link #saveEditedPlaylist}, for a replace of the tracks alone. */
    public void replaceEditedPlaylist(String playlistId, ArrayList<String> songsId, List<Child> songs, EditableSongs loaded, PlaylistActionCallback callback) {
        if (loaded != null && loaded.fromPhone) {
            queueReplace(playlistId, songsId, songs, null, loaded.ids(), callback);
        } else {
            createPlaylist(playlistId, null, songsId, callback);
        }
    }

    /** A null song list keeps the phone's current list, for a rename. */
    private void queueReplace(String playlistId, List<String> songsId, List<Child> songs, String name, List<String> baseIfNew, PlaylistActionCallback callback) {
        queue(playlistId, current -> songsId != null ? new ArrayList<>(songsId) : current, baseIfNew, songs, name,
                callback != null ? callback::onQueued : null,
                callback != null ? callback::onFailure : null,
                callback != null ? callback::onEmptied : null);
    }

    private void queue(String playlistId, Function<List<String>, List<String>> edit, List<String> baseIfNew, List<Child> songs, String name, Runnable onQueued, Runnable onFailure, Runnable onEmptied) {
        pendingEdits.queueReplace(playlistId, edit, baseIfNew, songs, name, new PendingPlaylistEditRepository.QueueResult() {
            @Override
            public void onQueued() {
                notifyPlaylistChanged();
                if (onQueued != null) onQueued.run();
            }

            @Override
            public void onRefused() {
                Toast.makeText(App.getContext(), R.string.playlist_offline_edit_refused, Toast.LENGTH_SHORT).show();
                if (onFailure != null) onFailure.run();
            }

            @Override
            public void onEmptied() {
                if (onEmptied != null) onEmptied.run();
            }

            @Override
            public void onFailed() {
                if (onFailure != null) onFailure.run();
            }
        });
    }

    /**
     * Whether the answer came over the network. With no network the HTTP cache answers instead, a
     * stored copy of an earlier answer or a made-up 504, and neither says anything about the
     * server, so a write answered that way has to wait on the phone.
     */
    static boolean reachedServer(Response<?> response) {
        return response.raw().networkResponse() != null;
    }

    static boolean sameSongIds(List<PlaylistSong> cached, List<Child> songs) {
        if (cached == null || cached.size() != songs.size()) return false;
        Set<String> cachedIds = new HashSet<>();
        for (PlaylistSong song : cached) cachedIds.add(song.getId());
        for (Child song : songs) {
            if (!cachedIds.contains(song.getId())) return false;
        }
        return true;
    }

    /**
     * Subsonic reports a refusal as HTTP 200 carrying a failed status, so the status line on its own
     * reads a refusal as a success.
     */
    static boolean isAccepted(Response<ApiResponse> response) {
        if (!response.isSuccessful() || response.body() == null) return false;

        SubsonicResponse subsonicResponse = response.body().getSubsonicResponse();

        return subsonicResponse != null && ResponseStatus.OK.equals(subsonicResponse.getStatus());
    }

    private Callback<ApiResponse> resultHandler(PlaylistActionCallback callback) {
        return new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (isAccepted(response)) {
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
        };
    }

    /**
     * Known limitation: the rename runs first and is not undone when the contents fail, so such a
     * save leaves the playlist renamed while reporting failure. The rename also writes the new
     * name to the local database, so the app's own playlist lists show the new name while the
     * open page still shows the old one.
     *
     * The failure path deliberately does not refresh, a reload through the server that just failed
     * can itself come back as an HTTP error, which this app reports as the playlist being gone and
     * closes the page. The success paths do refresh.
     */
    private void replacePlaylistSongs(String playlistId, ArrayList<String> songsId, List<Child> songs, PlaylistActionCallback callback) {
        if (songsId == null || songsId.isEmpty()) {
            notifyPlaylistChanged();
            if (callback != null) callback.onSuccess();
            return;
        }

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .createPlaylist(playlistId, null, songsId)
                .enqueue(replaceHandler(playlistId, songsId, songs, null, callback));
    }

    /** Like resultHandler, except that a replace the server never received waits on the phone. */
    private Callback<ApiResponse> replaceHandler(String playlistId, ArrayList<String> songsId, List<Child> songs, String name, PlaylistActionCallback callback) {
        Callback<ApiResponse> sent = resultHandler(callback);
        return new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (!reachedServer(response)) {
                    onFailure(call, new IOException("Answered from the cache"));
                    return;
                }
                sent.onResponse(call, response);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                queueReplace(playlistId, songsId, songs, name, null, callback);
            }
        };
    }

    @OptIn(markerClass = UnstableApi.class)
    private void updateLocalPlaylistName(String id, String newName) {
        new Thread(() -> {
            playlistDao.updateName(id, newName);
        }).start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void updateLastPlayed(String playlistId) {
        new Thread(() -> {
            playlistDao.updateLastPlayed(playlistId, System.currentTimeMillis());
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

    // Keyed by the server URL, not the server id preference, which only one of the two login screens writes.
    public LiveData<Boolean> isKeptSynced(String id) {
        String server = Preferences.getServer();
        if (server == null) return new MutableLiveData<>(false);
        return syncedPlaylistDao.isSynced(id, server);
    }

    /** Returns false, and writes nothing, when there is no server address to key the flag by. */
    public boolean setKeptSynced(String id, boolean kept) {
        String server = Preferences.getServer();
        if (server == null) {
            android.util.Log.w("PlaylistRepository", "No server address in use, keep synced not saved for " + id);
            return false;
        }
        // One writer thread keeps a fast on then off in order.
        SYNCED_WRITES.execute(() -> {
            if (kept) {
                syncedPlaylistDao.add(id, server);
            } else {
                syncedPlaylistDao.remove(id, server);
            }
        });
        return true;
    }

    // Never from the cache, a stale copy would miss exactly the tracks this exists to catch, so offline this does nothing.
    @OptIn(markerClass = UnstableApi.class)
    public void syncKeptPlaylists(Context context) {
        String server = Preferences.getServer();
        if (server == null || !DownloadRepair.isUserAuthenticated()) return;
        // Once per server per app open, and a failed fetch hands the gate back so an offline open does not spend it.
        if (server.equals(keptSyncRanFor)) return;
        keptSyncRanFor = server;
        new Thread(() -> {
            List<Playlist> kept = syncedPlaylistDao.getAllSynced(server);
            if (kept.isEmpty()) return;

            new Handler(Looper.getMainLooper()).post(() -> {
                for (Playlist playlist : kept) {
                    LiveData<List<Child>> songs = getPlaylistSongs(playlist.getId(), false);
                    songs.observeForever(new androidx.lifecycle.Observer<List<Child>>() {
                        @Override
                        public void onChanged(List<Child> children) {
                            songs.removeObserver(this);
                            if (children == null) {
                                keptSyncRanFor = null;
                                return;
                            }
                            DownloadUtil.downloadMissing(context, children, playlist.getId(), playlist.getName(), sent -> {});
                        }
                    });
                }
            });
        }).start();
    }

    public static void resetKeptSync() {
        keptSyncRanFor = null;
    }

    public interface PlaylistActionCallback {
        void onSuccess();
        void onFailure();

        default void onEmptied() {
            onFailure();
        }

        /** The change was kept on this phone, to be synced when the server can be reached. */
        default void onQueued() {
            onSuccess();
        }
    }

    public void deletePlaylist(String playlistId, PlaylistActionCallback callback) {
        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .deletePlaylist(playlistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (isAccepted(response)) {
                            String server = Preferences.getServer();
                            new Thread(() -> {
                                playlistSongDao.deleteForPlaylist(playlistId);
                                if (server != null) syncedPlaylistDao.remove(playlistId, server);
                                forgetOrderAndEdits(playlistId);
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
    public void insertIfAbsent(Playlist playlist) {
        InsertIfAbsentThreadSafe insert = new InsertIfAbsentThreadSafe(playlistDao, playlist);
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
                                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
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

    /**
     * Makes sure a playlist has a cached row without touching one that is already there. A replace
     * would overwrite every column of an existing row, and pinning has no business rewriting a row
     * it did not fetch.
     */
    private static class InsertIfAbsentThreadSafe implements Runnable {
        private final PlaylistDao playlistDao;
        private final Playlist playlist;

        public InsertIfAbsentThreadSafe(PlaylistDao playlistDao, Playlist playlist) {
            this.playlistDao = playlistDao;
            this.playlist = playlist;
        }

        @Override
        public void run() {
            playlistDao.insertIfAbsent(playlist);
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
