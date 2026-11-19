package com.eddyizm.tempus.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.repository.ArtistRepository;
import com.eddyizm.tempus.service.DownloaderManager;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.ArtistID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Starred2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Downloads what is starred on the server and not on the device yet: the starred songs, or every
 * song of the starred albums or artists. Shared by the settings dialogs, the home screen cards
 * and starring an album or artist while its sync is on.
 *
 * The work holds only the application context, so it finishes after the dialog or card that
 * started it is gone, and DownloadUtil.downloadMissing skips what is already downloaded or already
 * asked for. A sync that is still waiting on the server when every download is removed queues
 * nothing (see DownloaderManager.getRemovalGeneration).
 *
 * Call on the main thread.
 */
@OptIn(markerClass = UnstableApi.class)
public final class StarredSync {
    private static final String TAG = "StarredSync";

    private StarredSync() {
    }

    public static void syncSongs(Context context) {
        Context appContext = context.getApplicationContext();
        int generation = DownloaderManager.getRemovalGeneration();
        fetchStarred(starred -> queue(appContext, starred.getSongs(), generation));
    }

    public static void syncAlbums(Context context) {
        Context appContext = context.getApplicationContext();
        AlbumRepository albumRepository = new AlbumRepository();
        int generation = DownloaderManager.getRemovalGeneration();
        fetchStarred(starred -> collect(appContext, starred.getAlbums(), generation,
                (album, onSongs) -> albumRepository.getAlbumTracks(album.getId(), onSongs::accept)));
    }

    public static void syncArtists(Context context) {
        Context appContext = context.getApplicationContext();
        ArtistRepository artistRepository = new ArtistRepository();
        int generation = DownloaderManager.getRemovalGeneration();
        fetchStarred(starred -> collect(appContext, starred.getArtists(), generation,
                (artist, onSongs) -> artistRepository.getArtistAllSongs(artist.getId(), onSongs::accept)));
    }

    /** One album just starred, while album sync is on. */
    public static void syncAlbum(Context context, String albumId) {
        Context appContext = context.getApplicationContext();
        int generation = DownloaderManager.getRemovalGeneration();
        new AlbumRepository().getAlbumTracks(albumId, songs -> queue(appContext, songs, generation));
    }

    /** One artist just starred, while artist sync is on. */
    public static void syncArtist(Context context, String artistId) {
        Context appContext = context.getApplicationContext();
        int generation = DownloaderManager.getRemovalGeneration();
        new ArtistRepository().getArtistAllSongs(artistId, songs -> queue(appContext, songs, generation));
    }

    /**
     * Gathers the songs of every album or artist, then queues them all at once.
     *
     * @param songsOf fetches one item's songs. Both repositories always answer, with an empty list
     *                when a request fails, so the count of pending items always reaches zero.
     */
    private static <T> void collect(Context context, List<T> items, int generation, BiConsumer<T, Consumer<List<Child>>> songsOf) {
        if (items == null || items.isEmpty()) return;

        List<Child> songs = new ArrayList<>();
        int[] remaining = {items.size()};
        for (T item : items) {
            songsOf.accept(item, itemSongs -> {
                if (itemSongs != null) songs.addAll(itemSongs);
                if (--remaining[0] == 0) queue(context, songs, generation);
            });
        }
    }

    private static void fetchStarred(Consumer<Starred2> onLoaded) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        Starred2 starred = response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null
                                ? response.body().getSubsonicResponse().getStarred2()
                                : null;

                        if (starred == null) {
                            Log.w(TAG, "No starred items in the response, HTTP " + response.code());
                            return;
                        }
                        onLoaded.accept(starred);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.w(TAG, "Failed to fetch the starred items", t);
                    }
                });
    }

    /** @param generation {@link DownloaderManager#getRemovalGeneration()} when the sync started. */
    private static void queue(Context context, List<Child> songs, int generation) {
        if (songs == null || songs.isEmpty()) return;
        if (generation != DownloaderManager.getRemovalGeneration()) {
            Log.i(TAG, "Every download was removed during the sync; not queueing " + songs.size() + " songs");
            return;
        }

        DownloadUtil.downloadMissing(context, songs, null, null, queued -> {
            if (queued <= 0) return;
            Toast.makeText(context,
                    context.getResources().getQuantityString(R.plurals.songs_download_started, queued, queued),
                    Toast.LENGTH_SHORT).show();
        });
    }
}
