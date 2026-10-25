package com.eddyizm.tempus.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.PendingPlaylistEditDao;
import com.eddyizm.tempus.database.dao.PlaylistDao;
import com.eddyizm.tempus.database.dao.PlaylistSnapshotDao;
import com.eddyizm.tempus.database.dao.PlaylistSongDao;
import com.eddyizm.tempus.model.PendingPlaylistEdit;
import com.eddyizm.tempus.model.PlaylistSnapshot;
import com.eddyizm.tempus.model.PlaylistSong;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.PendingEditFold;
import com.eddyizm.tempus.util.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Keeps playlist changes made while the server could not be reached, one pending edit per
 * playlist and server, until {@link PlaylistFlush} merges it onto the server's list. The cached
 * playlist is rewritten at once, so the app shows the change before it is synced.
 */
@OptIn(markerClass = UnstableApi.class)
public class PendingPlaylistEditRepository {
    // One writer, so two quick edits fold in the order they were made.
    static final ExecutorService WRITES = Executors.newSingleThreadExecutor();

    private final PendingPlaylistEditDao pendingDao = AppDatabase.getInstance().pendingPlaylistEditDao();
    private final PlaylistSnapshotDao snapshotDao = AppDatabase.getInstance().playlistSnapshotDao();
    private final PlaylistSongDao playlistSongDao = AppDatabase.getInstance().playlistSongDao();
    private final PlaylistDao playlistDao = AppDatabase.getInstance().playlistDao();

    public interface QueueResult {
        void onQueued();

        /** There is no known order of the playlist to start an edit from. */
        void onRefused();

        void onEmptied();

        /** The edit no longer applies to the list, such as a remove of a track that is gone. */
        void onFailed();
    }

    /**
     * Applies edit to the phone's current version of the playlist and keeps the result. The current
     * version is the pending edit's list, or for a playlist without one, baseIfNew when the caller
     * loaded the list it edited, otherwise the server's last known order. It is read on the writer,
     * so a sync settling meanwhile cannot slip between the read and the write. knownSongs supplies
     * track details for the cache, so tracks added offline show their names. A name equal to the
     * cached one is not a rename, so an unchanged name never overwrites a rename made elsewhere.
     * Results arrive on the main thread.
     */
    public void queueReplace(String playlistId, Function<List<String>, List<String>> edit, @Nullable List<String> baseIfNew, @Nullable List<Child> knownSongs, @Nullable String newName, QueueResult result) {
        WRITES.execute(() -> {
            String server = Preferences.getServer();
            if (server == null) {
                post(result::onFailed);
                return;
            }

            PendingPlaylistEdit existing = pendingDao.get(playlistId, server);
            PlaylistSnapshot snapshot = snapshotDao.get(playlistId, server);
            List<String> startFrom = baseIfNew != null ? baseIfNew : snapshot != null ? snapshot.getSongIds() : null;
            List<String> base = existing != null ? existing.getBaseIds() : startFrom;
            List<String> current = existing != null ? existing.getLocalIds() : startFrom;
            if (current == null) {
                post(result::onRefused);
                return;
            }

            List<String> localIds = edit.apply(current);
            if (localIds == null) {
                post(result::onFailed);
                return;
            }
            if (localIds.isEmpty() && base != null && !base.isEmpty()) {
                post(result::onEmptied);
                return;
            }

            String rename = newName != null && !newName.equals(playlistDao.getName(playlistId)) ? newName : null;
            PendingPlaylistEdit folded = PendingEditFold.fold(existing, startFrom, playlistId, server, localIds, rename, System.currentTimeMillis());
            if (folded == null) {
                post(result::onRefused);
                return;
            }

            pendingDao.upsert(folded);
            cacheSongs(playlistId, knownSongs);
            if (rename != null) playlistDao.updateName(playlistId, rename);

            post(() -> {
                result.onQueued();
                PlaylistFlush.kick();
            });
        });
    }

    // playlist_song has a foreign key to playlist, and only tracks with details are worth keeping.
    private void cacheSongs(String playlistId, @Nullable List<Child> songs) {
        if (songs == null || !playlistDao.exists(playlistId)) return;

        List<PlaylistSong> rows = new ArrayList<>();
        for (Child song : songs) {
            if (song.getTitle() != null) rows.add(new PlaylistSong(playlistId, song));
        }
        if (!rows.isEmpty()) playlistSongDao.insertAll(rows);
    }

    /** Touches the database, so callers have to be off the main thread. */
    public boolean hasRow(String playlistId) {
        String server = Preferences.getServer();
        return server != null && pendingDao.get(playlistId, server) != null;
    }

    public LiveData<PendingPlaylistEdit> observe(String playlistId) {
        String server = Preferences.getServer();
        if (server == null) return new MutableLiveData<>(null);
        return pendingDao.observe(playlistId, server);
    }

    private static void post(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
