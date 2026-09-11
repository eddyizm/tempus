package com.eddyizm.tempus.util;

import android.util.Log;

import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.DownloadDao;
import com.eddyizm.tempus.subsonic.Subsonic;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ResponseStatus;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Response;

/**
 * Fills in the album artist for downloads that were stored before the app kept one. An album id is
 * unique only within one server, so the title has to agree as well as the id, and an album is
 * filled only when every unfilled row under that id agrees or not at all. Same title under the same
 * id on two servers is still not separable.
 */
@UnstableApi
public class AlbumArtistBackfill {
    private static final String TAG = "AlbumArtistBackfill";

    // ErrorCode holds this, but its companion members are plain vars and will not serve as a Java constant.
    private static final int DATA_NOT_FOUND = 70;

    private static final int ABORT_AFTER_FAILURES = 3;

    private static final int MAX_ATTEMPTS = 3;

    private static final int BATCH_SIZE = 50;

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static final AtomicBoolean ASKED_THIS_PROCESS = new AtomicBoolean(false);

    private AlbumArtistBackfill() {
    }

    public static void backfillIfNeeded() {
        if (!RUNNING.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                backfill();
            } catch (Exception exception) {
                ASKED_THIS_PROCESS.set(false);
                Log.w(TAG, "Album artist backfill failed", exception);
            } finally {
                RUNNING.set(false);
            }
        }, "AlbumArtistBackfill").start();
    }

    private static void backfill() {
        // Not in backfillIfNeeded: reaching this opens the database, and that caller is the main thread.
        int schemaVersion = AppDatabase.getInstance().getOpenHelper().getReadableDatabase().getVersion();

        if (Preferences.getBackfilledAlbumArtistVersion() == schemaVersion) return;

        // Zeroed before the stamp moves, so a write that only half lands gives extra passes and never fewer.
        if (Preferences.getAlbumArtistAttemptsVersion() != schemaVersion) {
            Preferences.setAlbumArtistAttempts(0);
            Preferences.setAlbumArtistAttemptsVersion(schemaVersion);
        }

        if (!isUserAuthenticated()) return;

        DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();

        // DownloadRepair runs beside this one and can still be restoring, so an empty table says nothing yet.
        int downloadedAtStart = downloadDao.countDownloaded();

        if (downloadedAtStart == 0) return;

        List<String> albumIds = downloadDao.getAlbumIdsWithoutAlbumArtist();

        // MainActivity has no configChanges, so turning the screen recreates it and calls this again.
        if (!ASKED_THIS_PROCESS.compareAndSet(false, true)) return;

        // Resolved once, so switching servers mid pass cannot retarget the remaining albums.
        Subsonic subsonic = App.getSubsonicClientInstance(false);

        List<Album> batch = new ArrayList<>();
        int filled = 0;
        int consecutiveFailures = 0;
        boolean retryLater = false;

        try {
            for (String albumId : albumIds) {
                if (!isUserAuthenticated()) {
                    retryLater = true;
                    break;
                }

                AlbumID3 album;

                try {
                    album = fetchAlbum(subsonic, albumId);
                    consecutiveFailures = 0;
                } catch (Exception exception) {
                    // Anything thrown counts, since a body this cannot parse is one album and not the library.
                    if (++consecutiveFailures < ABORT_AFTER_FAILURES) {
                        Log.w(TAG, "Skipped album " + albumId, exception);
                        continue;
                    }

                    retryLater = true;

                    Log.w(TAG, "Stopped the backfill, " + consecutiveFailures + " failures in a row", exception);
                    break;
                }

                if (album == null) continue;

                String albumArtist = albumArtistOf(album);

                if (albumArtist == null || albumArtist.trim().isEmpty()) continue;

                String albumTitle = album.getName();

                if (albumTitle == null || albumTitle.trim().isEmpty()) {
                    Log.w(TAG, "Skipped album " + albumId + ": the server answered without a name");
                    continue;
                }

                batch.add(new Album(albumId, albumTitle, albumArtist));

                if (batch.size() >= BATCH_SIZE) filled += flush(downloadDao, batch);
            }
        } finally {
            filled += flush(downloadDao, batch);
            Log.d(TAG, "Filled the album artist on " + filled + " of " + albumIds.size() + " albums");
        }

        // The walk goes back, or a process outliving the outage would sit on a stale library.
        if (retryLater) {
            ASKED_THIS_PROCESS.set(false);
            return;
        }

        // A table that changed size under the walk counts as unfinished, since the last look at it is
        // a snapshot of something still moving, and retiring on it would strand whatever lands next.
        boolean unfinished = downloadDao.countDownloaded() != downloadedAtStart
                || !downloadDao.getAlbumIdsWithoutAlbumArtist().isEmpty();

        if (unfinished) {
            int attempts = Preferences.getAlbumArtistAttempts() + 1;
            Preferences.setAlbumArtistAttempts(attempts);

            if (attempts < MAX_ATTEMPTS) return;
        }

        Preferences.setAlbumArtistBackfilled(schemaVersion);
    }

    /** Only where it is still missing, so a download made during the pass keeps what it arrived with. */
    private static int flush(DownloadDao downloadDao, List<Album> batch) {
        if (batch.isEmpty()) return 0;

        int[] filled = {0};

        AppDatabase.getInstance().runInTransaction(() -> {
            for (Album album : batch) {
                int elsewhere = downloadDao.countUnfilledUnderAnotherTitle(album.id, album.title);

                if (elsewhere > 0) {
                    Log.w(TAG, "Left album " + album.id + " alone, " + elsewhere
                            + " unfilled rows under it are not filed as " + album.title);
                    continue;
                }

                if (downloadDao.setAlbumArtist(album.id, album.title, album.artist) > 0) {
                    filled[0]++;
                    continue;
                }

                Log.w(TAG, "Album " + album.id + " changed no row as " + album.title);
            }
        });

        batch.clear();

        return filled[0];
    }

    private static final class Album {
        private final String id;
        private final String title;
        private final String artist;

        private Album(String id, String title, String artist) {
            this.id = id;
            this.title = title;
            this.artist = artist;
        }
    }

    private static AlbumID3 fetchAlbum(Subsonic subsonic, String albumId) throws IOException {
        Response<ApiResponse> response = subsonic
                .getBrowsingClient()
                .getAlbum(albumId)
                .execute();

        if (!response.isSuccessful()) {
            throw new IOException("getAlbum for " + albumId + " returned HTTP " + response.code());
        }

        if (response.body() == null) {
            throw new IOException("getAlbum for " + albumId + " returned an empty body");
        }

        SubsonicResponse subsonicResponse = response.body().getSubsonicResponse();

        if (subsonicResponse == null) {
            throw new IOException("getAlbum for " + albumId + " returned no subsonic response");
        }

        if (ResponseStatus.FAILED.equals(subsonicResponse.getStatus())) {
            com.eddyizm.tempus.subsonic.models.Error error = subsonicResponse.getError();

            if (error == null || error.getCode() == null) {
                throw new IOException("getAlbum for " + albumId + " failed without an error code");
            }

            // Only this album is gone. Anything else answers the same for every album, so it counts.
            if (error.getCode() == DATA_NOT_FOUND) return null;

            throw new IOException("getAlbum for " + albumId + " failed with " + error.getCode()
                    + " " + error.getMessage());
        }

        AlbumID3 album = subsonicResponse.getAlbum();

        if (album == null) {
            throw new IOException("getAlbum for " + albumId + " succeeded without an album");
        }

        return album;
    }

    /** The album artist under its OpenSubsonic name where the server sends that, and the classic one otherwise. */
    private static String albumArtistOf(AlbumID3 album) {
        String displayArtist = album.getDisplayArtist();

        if (displayArtist != null && !displayArtist.trim().isEmpty()) return displayArtist;

        return album.getArtist();
    }

    private static boolean isUserAuthenticated() {
        return Preferences.getPassword() != null
                || (Preferences.getToken() != null && Preferences.getSalt() != null);
    }
}
