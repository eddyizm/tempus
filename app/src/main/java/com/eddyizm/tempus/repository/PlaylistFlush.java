package com.eddyizm.tempus.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.PendingPlaylistEditDao;
import com.eddyizm.tempus.database.dao.PlaylistDao;
import com.eddyizm.tempus.database.dao.PlaylistSnapshotDao;
import com.eddyizm.tempus.model.PendingPlaylistEdit;
import com.eddyizm.tempus.model.PlaylistSnapshot;
import com.eddyizm.tempus.subsonic.api.playlist.PlaylistClient;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.PlaylistWithSongs;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;
import com.eddyizm.tempus.util.DownloadRepair;
import com.eddyizm.tempus.util.FlushDecision;
import com.eddyizm.tempus.util.PlaylistMerge;
import com.eddyizm.tempus.util.Preferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

/**
 * Sends pending playlist edits to the server. Each edit is merged onto the server's list as it is
 * now, read just before writing, so changes made elsewhere in the meantime survive. Runs only
 * while the app is in use: on login, when the network comes back, when a playlist is opened, after
 * a successful playlist read, and once a minute while the app is in the foreground, since a server
 * that comes back does not change the phone's network.
 * <p>
 * A pass stops at the first request that does not reach the server and leaves the rest pending.
 * A refusal marks that edit failed, and it waits for Retry sync or Discard phone changes.
 */
@OptIn(markerClass = UnstableApi.class)
public final class PlaylistFlush {
    private static final String TAG = "PlaylistFlush";
    private static final long THROTTLE_MS = 15_000;
    private static final long LOOP_MS = 60_000;

    private static final ExecutorService RUNNER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean scheduled = new AtomicBoolean();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile long lastThrottledKick;
    private static boolean looping;

    private static final Runnable LOOP = new Runnable() {
        @Override
        public void run() {
            if (!looping) return;
            kick();
            MAIN.postDelayed(this, LOOP_MS);
        }
    };

    private PlaylistFlush() {
    }

    public static void kick() {
        // One pass waiting is enough, it reads every pending edit when it starts.
        if (!scheduled.compareAndSet(false, true)) return;
        RUNNER.execute(() -> {
            scheduled.set(false);
            pass(null);
        });
    }

    public static void kick(String playlistId) {
        RUNNER.execute(() -> pass(playlistId));
    }

    public static void kickThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastThrottledKick < THROTTLE_MS) return;
        lastThrottledKick = now;
        kick();
    }

    /** Main thread only. */
    public static void startForegroundLoop() {
        if (looping) return;
        looping = true;
        MAIN.post(LOOP);
    }

    /** Main thread only. */
    public static void stopForegroundLoop() {
        looping = false;
        MAIN.removeCallbacks(LOOP);
    }

    public static void retry(String playlistId) {
        PendingPlaylistEditRepository.WRITES.execute(() -> {
            String server = Preferences.getServer();
            if (server == null) return;
            PendingPlaylistEdit edit = pending().get(playlistId, server);
            if (edit == null) return;
            pending().upsert(withState(edit, PendingPlaylistEdit.PENDING, null));
            kick(playlistId);
        });
    }

    /** Drops the phone's changes; the next read of the playlist comes from the server again. */
    public static void discard(String playlistId) {
        PendingPlaylistEditRepository.WRITES.execute(() -> {
            String server = Preferences.getServer();
            if (server != null) pending().remove(playlistId, server);
            MAIN.post(() -> new PlaylistRepository().notifyPlaylistChanged());
        });
    }

    private static void pass(String onlyPlaylistId) {
        String server = Preferences.getServer();
        if (server == null || !DownloadRepair.isUserAuthenticated()) return;

        List<PendingPlaylistEdit> edits;
        if (onlyPlaylistId != null) {
            PendingPlaylistEdit edit = pending().get(onlyPlaylistId, server);
            edits = edit != null && PendingPlaylistEdit.PENDING.equals(edit.getState()) ? Collections.singletonList(edit) : Collections.emptyList();
        } else {
            edits = pending().getAllPending(server);
        }

        for (PendingPlaylistEdit edit : edits) {
            try {
                if (!flushOne(edit, server)) return;
            } catch (IOException e) {
                Log.d(TAG, "Server not reachable, keeping pending playlist edits", e);
                return;
            }
        }
    }

    /** @return false when the server could not be reached and the pass should stop. */
    private static boolean flushOne(PendingPlaylistEdit edit, String server) throws IOException {
        String playlistId = edit.getPlaylistId();
        PlaylistClient client = App.getSubsonicClientInstance(false).getPlaylistClient();

        Response<ApiResponse> read = client.getPlaylist(playlistId).execute();
        SubsonicResponse readBody = read.body() != null ? read.body().getSubsonicResponse() : null;
        if (!PlaylistRepository.isAccepted(read) || readBody == null || readBody.getPlaylist() == null) {
            if (readBody != null && readBody.getError() != null && Integer.valueOf(70).equals(readBody.getError().getCode())) {
                String name = nameOf(playlistId);
                pending().remove(playlistId, server);
                toast(R.string.playlist_offline_sync_deleted, name);
                // Lets the repository clear the playlist's local rows the way it does for any gone playlist.
                MAIN.post(() -> new PlaylistRepository().getPlaylistSongs(playlistId, false));
                return true;
            }
            fail(edit, server, errorOf(read, readBody), R.string.playlist_offline_sync_failed);
            return true;
        }

        PlaylistWithSongs remotePlaylist = readBody.getPlaylist();
        List<String> remote = PlaylistRepository.idsOf(remotePlaylist.getEntries());
        FlushDecision decision = FlushDecision.decide(edit.getBaseIds(), edit.getLocalIds(), remote, edit.getNewName(), remotePlaylist.getName());

        if (decision.kind == FlushDecision.Kind.REFUSE_EMPTY) {
            fail(edit, server, "empty", R.string.playlist_offline_sync_empty);
            return true;
        }

        if (decision.kind == FlushDecision.Kind.WRITE) {
            if (decision.name != null) {
                Boolean isPublic = new PlaylistRepository().visibilityToSend(playlistId);
                Response<ApiResponse> renamed = client.updatePlaylist(playlistId, decision.name, isPublic, null, null).execute();
                if (!PlaylistRepository.isAccepted(renamed)) {
                    fail(edit, server, errorOf(renamed, renamed.body() != null ? renamed.body().getSubsonicResponse() : null), R.string.playlist_offline_sync_failed);
                    return true;
                }
            }
            if (decision.songs != null) {
                Response<ApiResponse> replaced = client.createPlaylist(playlistId, null, new ArrayList<>(decision.songs)).execute();
                if (!PlaylistRepository.isAccepted(replaced)) {
                    fail(edit, server, errorOf(replaced, replaced.body() != null ? replaced.body().getSubsonicResponse() : null), R.string.playlist_offline_sync_failed);
                    return true;
                }
            }
        }

        List<String> written = decision.songs != null ? decision.songs : remote;
        String writtenName = decision.name != null ? decision.name : remotePlaylist.getName();
        settle(edit, server, written, writtenName);
        MAIN.post(() -> new PlaylistRepository().notifyPlaylistChanged());
        return true;
    }

    /**
     * Clears the edit once the server holds it. An edit made on the phone while this one was being
     * sent is kept: what it changed relative to the list that was sent is carried onto the list
     * that was written, which becomes its new base, so it goes out on the next pass.
     */
    private static void settle(PendingPlaylistEdit sent, String server, List<String> written, String writtenName) {
        onWriter(() -> {
            String playlistId = sent.getPlaylistId();
            snapshots().upsert(new PlaylistSnapshot(playlistId, server, new ArrayList<>(written)));
            if (writtenName != null) playlists().updateName(playlistId, writtenName);

            PendingPlaylistEdit now = pending().get(playlistId, server);
            if (now == null) return;
            if (now.getUpdatedAt() == sent.getUpdatedAt()) {
                pending().remove(playlistId, server);
                return;
            }
            String name = now.getNewName() != null && !now.getNewName().equals(writtenName) ? now.getNewName() : null;
            List<String> local = PlaylistMerge.merge(sent.getLocalIds(), now.getLocalIds(), written);
            pending().upsert(new PendingPlaylistEdit(playlistId, server, new ArrayList<>(written), local, name, now.getUpdatedAt(), now.getState(), now.getLastError()));
        });
    }

    private static void fail(PendingPlaylistEdit edit, String server, String error, int message) {
        String name = nameOf(edit.getPlaylistId());
        onWriter(() -> {
            PendingPlaylistEdit now = pending().get(edit.getPlaylistId(), server);
            if (now != null) pending().upsert(withState(now, PendingPlaylistEdit.FAILED, error));
        });
        toast(message, name);
    }

    private static PendingPlaylistEdit withState(PendingPlaylistEdit edit, String state, String error) {
        return new PendingPlaylistEdit(edit.getPlaylistId(), edit.getServerId(), edit.getBaseIds(), edit.getLocalIds(), edit.getNewName(), edit.getUpdatedAt(), state, error);
    }

    // Runs on the writer that folds new edits, so a read and the write after it see no edit slip in between.
    private static void onWriter(Runnable runnable) {
        try {
            PendingPlaylistEditRepository.WRITES.submit(runnable).get();
        } catch (ExecutionException | InterruptedException e) {
            Log.w(TAG, "Pending playlist edit bookkeeping failed", e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private static String errorOf(Response<ApiResponse> response, SubsonicResponse body) {
        if (body != null && body.getError() != null) {
            String message = body.getError().getMessage();
            return message != null ? message : String.valueOf(body.getError().getCode());
        }
        return "HTTP " + response.code();
    }

    private static String nameOf(String playlistId) {
        String name = playlists().getName(playlistId);
        return name != null ? name : playlistId;
    }

    private static void toast(int message, String name) {
        MAIN.post(() -> Toast.makeText(App.getContext(), App.getContext().getString(message, name), Toast.LENGTH_LONG).show());
    }

    private static PendingPlaylistEditDao pending() {
        return AppDatabase.getInstance().pendingPlaylistEditDao();
    }

    private static PlaylistSnapshotDao snapshots() {
        return AppDatabase.getInstance().playlistSnapshotDao();
    }

    private static PlaylistDao playlists() {
        return AppDatabase.getInstance().playlistDao();
    }
}
