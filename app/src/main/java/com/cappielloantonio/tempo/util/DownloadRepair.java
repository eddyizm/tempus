package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadRequest;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.Subsonic;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.ResponseStatus;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

/**
 * Rebuilds the download table from the ExoPlayer download index.
 *
 * A version bump shipped without a matching migration, so upgrading from 4.22.x dropped every table
 * in tempo_db. The audio and the ExoPlayer index that tracks it are a separate store and survived,
 * leaving files on disk and an empty Download page. The index is keyed by the same song id the
 * download table uses, so any id present there and missing here is fetched from the server again.
 *
 * Recorded as done when the server answered for every song it was asked about and at least one row
 * came back. A pass that restores nothing is tried again on a later launch, up to MAX_ATTEMPTS,
 * because the likeliest cause is being pointed at the wrong server rather than a library that truly
 * lost every track the device still holds. After that it stops, so a user it can never help does not
 * pay for the scan forever.
 *
 * Two gaps. Song ids are unique only within one server, so downloads from two servers with small
 * integer ids can put one server's title on a row that plays the other's file. And restored rows
 * carry no playlist, because nothing on the device records which playlist a download came from.
 */
@UnstableApi
public class DownloadRepair {
    private static final String TAG = "DownloadRepair";

    // ErrorCode holds this, but its companion members are plain vars, so they are not usable as a
    // Java constant without going through Companion.
    private static final int DATA_NOT_FOUND = 70;

    private static final int BATCH_SIZE = 50;

    private static final int ABORT_AFTER_FAILURES = 3;

    private static final int MAX_ATTEMPTS = 3;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private DownloadRepair() {
    }

    public static void repairIfNeeded(Context context) {
        if (!RUNNING.compareAndSet(false, true)) return;

        Context applicationContext = context.getApplicationContext();

        new Thread(() -> {
            try {
                repair(applicationContext);
            } catch (Exception exception) {
                Log.w(TAG, "Download repair failed", exception);
            } finally {
                RUNNING.set(false);
            }
        }, "DownloadRepair").start();
    }

    private static void repair(Context context) {
        // Not in repairIfNeeded: reaching this opens the database, and that caller is the main thread.
        int schemaVersion = AppDatabase.getInstance().getOpenHelper().getReadableDatabase().getVersion();

        if (Preferences.getRepairedDownloadDatabaseVersion() == schemaVersion) return;
        if (!isUserAuthenticated()) return;

        List<CachedDownload> cached = readCachedDownloads(context);
        if (cached == null) return;

        DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();

        List<CachedDownload> missing = new ArrayList<>();
        for (CachedDownload entry : cached) {
            if (downloadDao.getOne(entry.id) == null) missing.add(entry);
        }

        if (missing.isEmpty()) {
            markRepaired(schemaVersion);
            return;
        }

        // Resolved once, so switching servers mid pass cannot retarget the remaining songs.
        Subsonic subsonic = App.getSubsonicClientInstance(false);

        List<Download> batch = new ArrayList<>();
        int restored = 0;
        int consecutiveFailures = 0;
        boolean retryLater = false;

        try {
            for (CachedDownload entry : missing) {
                Child song;

                try {
                    song = fetchSong(subsonic, entry.id);
                    consecutiveFailures = 0;
                } catch (IOException exception) {
                    // One failure says nothing about the next song, so keep going. A run of them is
                    // the server rather than the songs, and the rest of the list would only buy a
                    // request timeout each.
                    retryLater = true;

                    if (++consecutiveFailures < ABORT_AFTER_FAILURES) {
                        Log.w(TAG, "Skipped " + entry.id, exception);
                        continue;
                    }

                    Log.w(TAG, "Stopped the repair, " + consecutiveFailures + " failures in a row", exception);
                    return;
                }

                // Gone as far as this server is concerned.
                if (song == null) {
                    Log.d(TAG, "Skipped " + entry.id + ": the server no longer has it");
                    continue;
                }

                // The row is keyed by the returned id, which the index would never match again.
                if (!entry.id.equals(song.getId())) {
                    Log.w(TAG, "Skipped " + entry.id + ": the server answered with id " + song.getId());
                    continue;
                }

                Download download = new Download(song);
                download.setDownloadState(1);
                download.setDownloadUri(entry.uri);
                applyTranscodeFromUri(download, entry.uri);

                batch.add(download);

                // Batched because the Downloads screen redraws on every insert.
                if (batch.size() >= BATCH_SIZE) restored += flush(context, downloadDao, batch);
            }
        } finally {
            // So an abort, or a throw nothing here predicts, still commits what was fetched.
            restored += flush(context, downloadDao, batch);
            Log.d(TAG, "Restored " + restored + " of " + missing.size() + " download rows");
        }

        if (retryLater) return;

        if (restored > 0) {
            markRepaired(schemaVersion);
            return;
        }

        // Nothing came back. Worth another launch or two in case this was the wrong server, but not
        // worth a full scan and a request per song on every launch for the rest of time.
        int attempts = Preferences.getDownloadRepairAttempts() + 1;
        Preferences.setDownloadRepairAttempts(attempts);

        if (attempts >= MAX_ATTEMPTS) markRepaired(schemaVersion);
    }

    /**
     * A row can wait in the batch for many round trips, and the user can act on the Downloads screen
     * meanwhile, so the checks belong here rather than next to the fetch.
     */
    private static int flush(Context context, DownloadDao downloadDao, List<Download> batch) {
        if (batch.isEmpty()) return 0;

        List<Download> writable = new ArrayList<>();

        for (Download download : batch) {
            if (downloadDao.getOne(download.getId()) != null) continue;
            if (!DownloadUtil.getDownloadTracker(context).isDownloaded(download.getId())) continue;

            writable.add(download);
        }

        batch.clear();

        if (writable.isEmpty()) return 0;

        // Never overwrite: a row written since the check carries a live state and a playlist.
        downloadDao.insertAllKeepingExisting(writable);

        return writable.size();
    }

    // The schema version rather than a flag, so a later version repeating this finds it armed.
    private static void markRepaired(int schemaVersion) {
        Preferences.setDownloadDatabaseRepaired(schemaVersion);
    }

    private static boolean isUserAuthenticated() {
        return Preferences.getPassword() != null
                || (Preferences.getToken() != null && Preferences.getSalt() != null);
    }

    // Null when the index cannot be read: a reason to retry, not an empty result to act on.
    private static List<CachedDownload> readCachedDownloads(Context context) {
        DownloadIndex index = DownloadUtil.getDownloadManager(context).getDownloadIndex();

        List<CachedDownload> cached = new ArrayList<>();

        try (DownloadCursor cursor = index.getDownloads(androidx.media3.exoplayer.offline.Download.STATE_COMPLETED)) {
            while (cursor.moveToNext()) {
                androidx.media3.exoplayer.offline.Download download = cursor.getDownload();

                // An index entry can outlive its bytes, and that row would claim a file that is gone.
                if (download.getBytesDownloaded() <= 0) continue;

                DownloadRequest request = download.request;
                cached.add(new CachedDownload(request.id, request.uri.toString()));
            }
        } catch (Exception exception) {
            Log.w(TAG, "Failed to read the download index", exception);
            return null;
        }

        return cached;
    }

    /**
     * Null means the server says the song is gone, which no later attempt improves on. Everything
     * else throws, because an unreadable answer looks exactly like a deleted track from here, and a
     * pass that restores anything at all is recorded as done, taking those songs down with it.
     */
    private static Child fetchSong(Subsonic subsonic, String id) throws IOException {
        Response<ApiResponse> response = subsonic
                .getBrowsingClient()
                .getSong(id)
                .execute();

        // An HTTP level refusal is about the connection or the credentials, not this song.
        if (!response.isSuccessful()) {
            throw new IOException("getSong for " + id + " returned HTTP " + response.code());
        }

        if (response.body() == null) {
            throw new IOException("getSong for " + id + " returned an empty body");
        }

        SubsonicResponse subsonicResponse = response.body().getSubsonicResponse();

        if (subsonicResponse == null) {
            throw new IOException("getSong for " + id + " returned no subsonic response");
        }

        if (ResponseStatus.FAILED.equals(subsonicResponse.getStatus())) {
            com.cappielloantonio.tempo.subsonic.models.Error error = subsonicResponse.getError();

            if (error == null || error.getCode() == null) {
                throw new IOException("getSong for " + id + " failed without an error code");
            }

            if (error.getCode() == DATA_NOT_FOUND) return null;

            throw new IOException("getSong for " + id + " failed with " + error.getCode()
                    + " " + error.getMessage());
        }

        Child song = subsonicResponse.getSong();

        if (song == null) {
            throw new IOException("getSong for " + id + " succeeded without a song");
        }

        return song;
    }

    /**
     * Same rewrite the live download path applies, but driven by the stored uri rather than the
     * current preferences, because the uri records what was actually requested at download time.
     */
    private static void applyTranscodeFromUri(Download download, String downloadUri) {
        Uri uri = Uri.parse(downloadUri);

        // getQueryParameter refuses an opaque uri.
        if (uri.isOpaque()) return;

        MusicUtil.applyTranscodeMetadata(download, uri.getQueryParameter("format"),
                uri.getQueryParameter("maxBitRate"));
    }

    private static class CachedDownload {
        private final String id;
        private final String uri;

        private CachedDownload(String id, String uri) {
            this.id = id;
            this.uri = uri;
        }
    }
}
