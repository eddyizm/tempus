package com.eddyizm.tempus.util;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.repository.AlbumRepository;
import com.eddyizm.tempus.repository.ArtistRepository;
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
 * song of the starred albums or artists. Shared by the settings dialogs and the home screen cards.
 *
 * It waits for the server's answer rather than observing a LiveData, whose first value is a
 * placeholder empty list: the dialogs used to take that placeholder, close, and either queue
 * nothing or queue the whole list later from an observer left on the activity. The work holds only
 * the application context, so it finishes after the dialog or card that started it is gone, and
 * DownloadUtil.downloadMissing skips what is already downloaded or already asked for.
 *
 * Call on the main thread.
 */
@UnstableApi
public final class StarredSync {
    private static final String TAG = "StarredSync";

    private StarredSync() {
    }

    public static void syncSongs(Context context) {
        Context appContext = context.getApplicationContext();
        fetchStarred(starred -> queue(appContext, starred.getSongs()));
    }

    public static void syncAlbums(Context context) {
        Context appContext = context.getApplicationContext();
        AlbumRepository albumRepository = new AlbumRepository();
        fetchStarred(starred -> collect(appContext, starred.getAlbums(),
                (album, onSongs) -> albumRepository.getAlbumTracks(album.getId(), onSongs::accept)));
    }

    public static void syncArtists(Context context) {
        Context appContext = context.getApplicationContext();
        ArtistRepository artistRepository = new ArtistRepository();
        fetchStarred(starred -> collect(appContext, starred.getArtists(),
                (artist, onSongs) -> artistRepository.getArtistAllSongs(artist.getId(), onSongs::accept)));
    }

    // Both repositories always answer, with an empty list when a request fails, so the count reaches zero.
    private static <T> void collect(Context context, List<T> items, BiConsumer<T, Consumer<List<Child>>> songsOf) {
        if (items == null || items.isEmpty()) return;

        List<Child> songs = new ArrayList<>();
        int[] remaining = {items.size()};
        for (T item : items) {
            songsOf.accept(item, itemSongs -> {
                if (itemSongs != null) songs.addAll(itemSongs);
                if (--remaining[0] == 0) queue(context, songs);
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

    private static void queue(Context context, List<Child> songs) {
        if (songs == null || songs.isEmpty()) return;

        DownloadUtil.downloadMissing(context, songs, null, null, queued -> {
            if (queued <= 0) return;
            Toast.makeText(context,
                    context.getResources().getQuantityString(R.plurals.songs_download_started, queued, queued),
                    Toast.LENGTH_SHORT).show();
        });
    }
}
