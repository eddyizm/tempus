package com.cappielloantonio.tempo.repository;

import static com.cappielloantonio.tempo.service.MediaManager.enqueue;

import androidx.annotation.NonNull;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@UnstableApi
public class InstantMixBuilder {
    private static final String TAG = "InstantMixBuilder";
    private final AutomotiveRepository repository;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public InstantMixBuilder(AutomotiveRepository repository) {
        this.repository = repository;
    }

    /**
     * Builds the rest of the Instant Mix in background and enqueues it.
     * Called from AndroidAutoMediaServiceExtension.handle() when nextMediaItemIndex == C.INDEX_UNSET
     * and the current item has parent_id starting with AA_INSTANTMIX_SOURCE.
     *
     * @param artistId      The artist ID extracted from parent_id
     * @param usedTrackId   The ID of the first track already playing, to avoid duplicate
     * @param count         Total number of tracks to add (INSTANT_MIX_MAX_TRACKS - 1)
     * @param browserFuture The MediaBrowser future to enqueue into
     */
    public void buildAndEnqueue(
            String artistId,
            String usedTrackId,
            int count,
            ListenableFuture<MediaBrowser> browserFuture) {

        if (!isRunning.compareAndSet(false, true)) {
            Log.d(TAG, "Build already running, skipping");
            return;
        }

        Log.d(TAG, "Building remaining " + count + " tracks for artist " + artistId);

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getArtist() != null
                                && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {

                            List<AlbumID3> albums = new ArrayList<>(
                                    response.body().getSubsonicResponse().getArtist().getAlbums()
                            );

                            if (albums.isEmpty()) {
                                Log.e(TAG, "Artist " + artistId + " has no albums, nothing to build");
                                isRunning.set(false);
                                return;
                            }

                            List<Child> mixTracks = new ArrayList<>();
                            Set<String> usedTrackIds = new HashSet<>();
                            usedTrackIds.add(usedTrackId);

                            Random random = new Random();

                            fetchNextTrack(albums, 0, mixTracks, usedTrackIds, random, count, count * 4, browserFuture);

                            isRunning.set(false);
                        } else {
                            Log.e(TAG, "Failed to retrieve albums for artistId=" + artistId);
                            isRunning.set(false);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Network failure while fetching artist: " + t.getMessage());
                        isRunning.set(false);
                    }
                });
    }

    /**
     * Recursively builds an instant mix by fetching tracks from randomly selected albums.
     * Each cycle shuffles the album list and fetches one track from albums[0], then one from albums[1],
     * alternating between the two until maxTracks is reached. The shuffle at the start of each cycle
     * ensures variety — consecutive cycles may pick from completely different albums.
     * Artists with a single album keep drawing from that album.
     * -
     * Each step consumes one attempt whether or not it yields a new track, so the recursion
     * (and the number of network calls) is hard-bounded by attemptsLeft even when the artist
     * has fewer unique tracks than maxTracks. Whatever was collected by then gets enqueued.
     *
     * @param albums        Full list of artist albums (never empty)
     * @param albumIndex    0 or 1 — which of the two albums to fetch in this step
     * @param mixTracks     Accumulated list of selected tracks
     * @param usedTrackIds  Set of already used track IDs to avoid duplicates
     * @param random        Shared Random instance for shuffling and track picking
     * @param maxTracks     Target number of tracks
     * @param attemptsLeft  Remaining fetch attempts before giving up on reaching maxTracks
     * @param browserFuture Future to enqueue into when the mix is complete
     */
    private void fetchNextTrack(
            List<AlbumID3> albums,
            int albumIndex,
            List<Child> mixTracks,
            Set<String> usedTrackIds,
            Random random,
            int maxTracks,
            int attemptsLeft,
            ListenableFuture<MediaBrowser> browserFuture) {

        if (mixTracks.size() >= maxTracks || attemptsLeft <= 0) {
            if (mixTracks.isEmpty()) {
                Log.w(TAG, "No tracks collected, nothing to enqueue");
                return;
            }
            Log.d(TAG, "Mix complete with " + mixTracks.size() + "/" + maxTracks + " tracks, enqueuing");
            repository.setChildrenMetadata(mixTracks);
            enqueue(browserFuture, mixTracks, true);
            return;
        }

        if (albumIndex == 0) {
            Collections.shuffle(albums, random);
            Log.d(TAG, "New cycle, albums shuffled");
        }

        AlbumID3 album = albums.get(albumIndex < albums.size() ? albumIndex : 0);
        Log.d(TAG, "Fetching album[" + albumIndex + "] " + album.getName()
                + " (" + mixTracks.size() + "/" + maxTracks + " tracks so far)");

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(album.getId())
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getAlbum() != null
                                && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {

                            List<Child> songs = response.body().getSubsonicResponse().getAlbum().getSongs();
                            Child candidate = songs.get(random.nextInt(songs.size()));

                            if (!usedTrackIds.contains(candidate.getId())) {
                                mixTracks.add(candidate);
                                usedTrackIds.add(candidate.getId());
                                Log.d(TAG, "Added track [" + mixTracks.size() + "/" + maxTracks + "] "
                                        + candidate.getTitle() + " from " + album.getName());
                            } else {
                                Log.d(TAG, "Track " + candidate.getTitle() + " already used, skipping");
                            }
                        } else {
                            Log.w(TAG, "Album " + album.getName() + " skipped (empty or failed)");
                        }

                        int nextIndex = (albumIndex == 0) ? 1 : 0;
                        fetchNextTrack(albums, nextIndex, mixTracks, usedTrackIds, random, maxTracks, attemptsLeft - 1, browserFuture);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Failed to load album " + album.getName() + ": " + t.getMessage());
                        int nextIndex = (albumIndex == 0) ? 1 : 0;
                        fetchNextTrack(albums, nextIndex, mixTracks, usedTrackIds, random, maxTracks, attemptsLeft - 1, browserFuture);
                    }
                });
    }
}