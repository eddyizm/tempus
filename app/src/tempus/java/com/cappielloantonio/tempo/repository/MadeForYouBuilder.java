package com.cappielloantonio.tempo.repository;

import static com.cappielloantonio.tempo.service.MediaManager.enqueue;
import static com.cappielloantonio.tempo.util.Preferences.getServerId;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ChronologyDao;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Starred2;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

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
public class MadeForYouBuilder {

    private static final String TAG = "MadeForYouBuilder";
    private final AutomotiveRepository repository;
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private enum MixStep { RECENT, STARRED_ALBUM, STARRED_ARTIST, STARRED_TRACKS}
    private static final int NUMBER_OF_RECENT_ALBUMS = 15;
    private static final int NUMBER_OF_RECENT_TRACKS = 50;
    private static final int MAX_CYCLES = 100; // safetyBreak

    public MadeForYouBuilder(AutomotiveRepository repository) {
        this.repository = repository;
    }

    /**
     * Builds the rest of the MadeForYou mix in background and enqueues it.
     * Called from TracksChangedExtension.handle() when nextMediaItemIndex == C.INDEX_UNSET
     * and the current item has parent_id starting with AA_MADE_FOR_YOU_SOURCE.
     *
     * @param mixType       AA_QUICKMIX_ID, AA_MYMIX_ID or AA_DISCOVERYMIX_ID
     * @param usedTrackId   The ID of the first track already playing, to avoid duplicate
     * @param count         Total number of tracks to add (INSTANT_MIX_MAX_TRACKS - 1)
     * @param browserFuture The MediaBrowser future to enqueue into
     */
    public void buildAndEnqueue(
            String mixType,
            String usedTrackId,
            int count,
            ListenableFuture<MediaBrowser> browserFuture) {

        if (!isRunning.compareAndSet(false, true)) {
            Log.d(TAG, mixType + " Build already running, skipping");
            return;
        }

        Log.d(TAG, mixType + " Building remaining " + count + " tracks");

        // QUICK_MIX : only recent albums needed
        if (mixType.equals(Constants.AA_QUICKMIX_ID)) {
            App.getSubsonicClientInstance(false)
                    .getAlbumSongListClient()
                    .getAlbumList2("recent", NUMBER_OF_RECENT_ALBUMS, 0, null, null)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful()
                                    && response.body() != null
                                    && response.body().getSubsonicResponse().getAlbumList2() != null
                                    && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {

                                List<AlbumID3> recentAlbums = new ArrayList<>(
                                        response.body().getSubsonicResponse().getAlbumList2().getAlbums());
                                Log.d(TAG, mixType + " recent albums loaded: " + recentAlbums.size());

                                Set<String> usedTrackIds = new HashSet<>();
                                usedTrackIds.add(usedTrackId);
                                Random random = new Random();

                                Collections.shuffle(recentAlbums, random);

                                runMixStep(1,
                                        recentAlbums, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                                        0, 0, 0, 0,
                                        new ArrayList<>(), usedTrackIds,
                                        count, mixType, Collections.emptySet(), browserFuture);
                            } else {
                                Log.w(TAG, mixType + " recent albums failed");
                                fallbackToRandomSongs(count, usedTrackId, browserFuture);
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            Log.e(TAG, mixType + " network failure: " + t.getMessage());
                            isRunning.set(false);
                        }
                    });
            return;
        }

        // MY_MIX and DISCOVERY_MIX : fetch recent + starred + recent track IDs in parallel
        SettableFuture<List<AlbumID3>> recentAlbumsFuture = SettableFuture.create();
        SettableFuture<List<AlbumID3>> starredAlbumsFuture = SettableFuture.create();
        SettableFuture<List<ArtistID3>> starredArtistsFuture = SettableFuture.create();
        SettableFuture<List<Child>> starredTracksFuture = SettableFuture.create();
        SettableFuture<Set<String>> recentTrackIdsFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("recent", NUMBER_OF_RECENT_ALBUMS, 0, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getAlbumList2() != null
                                && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            recentAlbumsFuture.set(response.body().getSubsonicResponse().getAlbumList2().getAlbums());
                        } else {
                            recentAlbumsFuture.set(Collections.emptyList());
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, mixType + " Failed to fetch recent albums: " + t.getMessage());
                        recentAlbumsFuture.set(Collections.emptyList());
                    }
                });

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getStarred2() != null) {
                            Starred2 starred = response.body().getSubsonicResponse().getStarred2();
                            starredAlbumsFuture.set(starred.getAlbums() != null ? starred.getAlbums() : Collections.emptyList());
                            starredArtistsFuture.set(starred.getArtists() != null ? starred.getArtists() : Collections.emptyList());
                            starredTracksFuture.set(starred.getSongs() != null ? starred.getSongs() : Collections.emptyList());
                        } else {
                            starredAlbumsFuture.set(Collections.emptyList());
                            starredArtistsFuture.set(Collections.emptyList());
                            starredTracksFuture.set(Collections.emptyList());
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, mixType + " Failed to fetch starred: " + t.getMessage());
                        starredAlbumsFuture.set(Collections.emptyList());
                        starredArtistsFuture.set(Collections.emptyList());
                        starredTracksFuture.set(Collections.emptyList());
                    }
                });

        // Load recent track IDs for Discovery Mix filtering
        chronologyDao.getLastPlayed(getServerId(), NUMBER_OF_RECENT_TRACKS)
                .observeForever(new Observer<List<Chronology>>() {
                    @Override
                    public void onChanged(List<Chronology> chronology) {
                        Set<String> ids = new HashSet<>();
                        if (chronology != null) {
                            for (Chronology c : chronology) ids.add(c.getId());
                        }
                        recentTrackIdsFuture.set(ids);
                        chronologyDao.getLastPlayed(getServerId(), NUMBER_OF_RECENT_TRACKS).removeObserver(this);
                    }
                });

        ListenableFuture<Void> phase1Future = Futures.whenAllSucceed(
                recentAlbumsFuture, starredAlbumsFuture, starredArtistsFuture, starredTracksFuture, recentTrackIdsFuture).call(() -> {

            List<AlbumID3> recentAlbums = new ArrayList<>(Futures.getDone(recentAlbumsFuture));
            List<AlbumID3> starredAlbums = new ArrayList<>(Futures.getDone(starredAlbumsFuture));
            List<ArtistID3> starredArtists = new ArrayList<>(Futures.getDone(starredArtistsFuture));
            List<Child> starredTracks = new ArrayList<>(Futures.getDone(starredTracksFuture));

            Set<String> recentTracks = Futures.getDone(recentTrackIdsFuture);

            Log.d(TAG, mixType + " recent=" + recentAlbums.size()
                    + " starredArtists=" + starredArtists.size()
                    + " starredAlbums=" + starredAlbums.size()
                    + " starredTracks=" + starredTracks.size()
                    + " recentTracks=" + recentTracks.size());

            if (recentAlbums.isEmpty() && starredAlbums.isEmpty() && starredArtists.isEmpty()) {
                Log.w(TAG, mixType + " No context available, falling back to random songs");
                fallbackToRandomSongs(count, usedTrackId, browserFuture);
                return null;
            }

            Set<String> usedTrackIds = new HashSet<>();
            usedTrackIds.add(usedTrackId);

            Collections.shuffle(recentAlbums);
            Collections.shuffle(starredAlbums);
            Collections.shuffle(starredArtists);
            Collections.shuffle(starredTracks);
            starredTracks.subList(0, Math.min(100, starredTracks.size()) );

            runMixStep(1,
                    recentAlbums, starredAlbums, starredArtists, starredTracks,
                    0, 0, 0, 0,
                    new ArrayList<>(), usedTrackIds,
                    count, mixType, recentTracks, browserFuture);

            return null;
        }, MoreExecutors.directExecutor());

        Futures.addCallback(phase1Future, new FutureCallback<Void>() {
            @Override public void onSuccess(Void result) {}
            @Override public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, mixType + " Phase 1 error: " + t.getMessage());
                isRunning.set(false);
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Returns the next MixStep based on current cycleIndex and mode, and reshuffles
     * source lists when a cycle completes.

     * QUICK_MIX:     cycle of 2 — recent[0], recent[1], then reshuffle
     * MY_MIX:        cycle of 4 — recent, starred (album OR artist) × 2, then reshuffle
     *                starred source depends on Preferences.isStarredAlbumsForMadeForYouEnabled()
     * DISCOVERY_MIX: same cycle as MY_MIX, similar songs handled separately
     */
    private MixStep getNextStep(
            int cycleIndex,
            String mixType,
            List<AlbumID3> recentAlbums,
            List<AlbumID3> starredAlbums,
            List<ArtistID3> starredArtists,
            List<Child> starredTracks) {

        if (mixType.equals(Constants.AA_QUICKMIX_ID)) {
            // cycle of 2: recent[0] → recent[1] → shuffle → repeat
            int posInCycle = cycleIndex % 2;
            if (posInCycle == 0) {
                Log.d(TAG, mixType + " Shuffle collections");
                Collections.shuffle(recentAlbums);
            }
            return MixStep.RECENT;
        } else {
            // MY_MIX and DISCOVERY_MIX: cycle of 4
            // Recent → STARRED → Recent → STARRED → shuffle → repeat
            int posInCycle = cycleIndex % 4;
            if (posInCycle == 0) {
                Log.d(TAG, mixType + " Shuffle collections");
                Collections.shuffle(recentAlbums);
                Collections.shuffle(starredAlbums);
                Collections.shuffle(starredArtists);
                Collections.shuffle(starredTracks);
            }
            switch (posInCycle) {
                case 0: case 2: return MixStep.RECENT;
                default:
                    switch (Preferences.getAndroidAutoStarredForMadeForYou()) {
                        case 2: return MixStep.STARRED_TRACKS;
                        case 1: return MixStep.STARRED_ALBUM;
                        case 0: default: return MixStep.STARRED_ARTIST;
                    }
            }
        }
    }

    /**
     * Recursively builds the MadeForYou mix by cycling through steps based on the selected mode.
     * When complete, enqueues directly via browserFuture.

     * Recursion depth is bounded by count.
     */
    private void runMixStep(
            int cycleIndex,
            List<AlbumID3> recentAlbums,
            List<AlbumID3> starredAlbums,
            List<ArtistID3> starredArtists,
            List<Child> starredTracks,
            int recentIdx, int starredAlbumIdx, int starredArtistIdx, int starredTracksIdx,
            List<Child> mixTracks, Set<String> usedTrackIds,
            int count, String mixType,
            Set<String> recentTrackIds,
            ListenableFuture<MediaBrowser> browserFuture) {

        if (mixTracks.size() >= count) {
            Log.d(TAG, mixType + " complete with " + mixTracks.size() + " tracks, enqueuing");
            repository.setChildrenMetadata(mixTracks);
            enqueue(browserFuture, mixTracks, true);
            isRunning.set(false);
            return;
        }

        if (cycleIndex > MAX_CYCLES) {
            Log.w(TAG, mixType + " Safety break reached, finalizing with " + mixTracks.size() + " tracks");
            repository.setChildrenMetadata(mixTracks);
            enqueue(browserFuture, mixTracks, true);
            isRunning.set(false);
            return;
        }

        MixStep currentStep = getNextStep(cycleIndex, mixType, recentAlbums, starredAlbums, starredArtists, starredTracks);

        switch (currentStep) {
            case RECENT:
                if (!recentAlbums.isEmpty()) {
                    AlbumID3 album = recentAlbums.get(recentIdx % recentAlbums.size());
                    Log.d(TAG, mixType + " Step RECENT cycle=" + cycleIndex + ": " + album.getName());
                    fetchTrackThenSimilar(album.getId(), MixStep.RECENT,
                            cycleIndex + 1,
                            recentAlbums, starredAlbums, starredArtists, starredTracks,
                            recentIdx + 1, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                            mixTracks, usedTrackIds, count, mixType,
                            recentTrackIds, browserFuture);
                } else {
                    Log.d(TAG, mixType + " Step RECENT: no recent albums, skipping");
                    runMixStep(cycleIndex + 1,
                            recentAlbums, starredAlbums, starredArtists, starredTracks, starredTracksIdx,
                            recentIdx, starredAlbumIdx, starredArtistIdx,
                            mixTracks, usedTrackIds, count, mixType,
                            recentTrackIds, browserFuture);
                }
                break;

            case STARRED_ALBUM:
                if (!starredAlbums.isEmpty()) {
                    AlbumID3 album = starredAlbums.get(starredAlbumIdx % starredAlbums.size());
                    Log.d(TAG, mixType + " Step STARRED_ALBUM cycle=" + cycleIndex + ": " + album.getName());
                    fetchTrackThenSimilar(album.getId(), MixStep.STARRED_ALBUM,
                            cycleIndex + 1,
                            recentAlbums, starredAlbums, starredArtists, starredTracks,
                            recentIdx, starredAlbumIdx + 1, starredArtistIdx, starredTracksIdx,
                            mixTracks, usedTrackIds, count, mixType,
                            recentTrackIds, browserFuture);
                } else {
                    Log.d(TAG, mixType + " Step STARRED_ALBUM: no starred albums, skipping");
                    runMixStep(cycleIndex + 1,
                            recentAlbums, starredAlbums, starredArtists, starredTracks,
                            recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                            mixTracks, usedTrackIds, count, mixType,
                            recentTrackIds, browserFuture);
                }
                break;

            case STARRED_ARTIST:
                if (!starredArtists.isEmpty()) {
                    ArtistID3 artist = starredArtists.get(starredArtistIdx % starredArtists.size());
                    Log.d(TAG, mixType + " Step STARRED_ARTIST cycle=" + cycleIndex + ": " + artist.getName());
                    App.getSubsonicClientInstance(false)
                            .getBrowsingClient()
                            .getArtist(artist.getId())
                            .enqueue(new Callback<ApiResponse>() {
                                @Override
                                public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                                    if (response.isSuccessful()
                                            && response.body() != null
                                            && response.body().getSubsonicResponse().getArtist() != null
                                            && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                                        List<AlbumID3> artistAlbums = response.body().getSubsonicResponse().getArtist().getAlbums();
                                        Collections.shuffle(artistAlbums);
                                        AlbumID3 album = artistAlbums.get(0);
                                        Log.d(TAG, mixType + " Artist " + artist.getName() + " albums " + album.getName());
                                        fetchTrackThenSimilar(album.getId(), MixStep.STARRED_ARTIST,
                                                cycleIndex + 1,
                                                recentAlbums, starredAlbums, starredArtists, starredTracks,
                                                recentIdx, starredAlbumIdx, starredArtistIdx + 1, starredTracksIdx,
                                                mixTracks, usedTrackIds, count, mixType,
                                                recentTrackIds, browserFuture);
                                    } else {
                                        Log.w(TAG, mixType + " Artist " + artist.getName() + " returned no albums, skipping");
                                        runMixStep(cycleIndex + 1,
                                                recentAlbums, starredAlbums, starredArtists, starredTracks,
                                                recentIdx, starredAlbumIdx, starredArtistIdx + 1, starredTracksIdx,
                                                mixTracks, usedTrackIds, count, mixType,
                                                recentTrackIds, browserFuture);
                                    }
                                }
                                @Override
                                public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                                    Log.e(TAG, mixType + " Failed to fetch artist albums: " + t.getMessage());
                                    runMixStep(cycleIndex + 1,
                                            recentAlbums, starredAlbums, starredArtists, starredTracks,
                                            recentIdx, starredAlbumIdx, starredArtistIdx + 1, starredTracksIdx,
                                            mixTracks, usedTrackIds, count, mixType,
                                            recentTrackIds, browserFuture);
                                }
                            });
                } else {
                    Log.d(TAG, mixType + " Step STARRED_ARTIST: no starred artists, skipping");
                    runMixStep(cycleIndex + 1,
                            recentAlbums, starredAlbums, starredArtists, starredTracks,
                            recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                            mixTracks, usedTrackIds, count, mixType,
                            recentTrackIds, browserFuture);
                }
                break;

            case STARRED_TRACKS:
                if (!starredTracks.isEmpty()) {
                    Child track = starredTracks.get(starredTracksIdx % starredTracks.size());
                    Log.d(TAG, mixType + " Step STARRED_TRACKS cycle=" + cycleIndex + ": " + track.getTitle());
                    addTrackThenSimilar(track,
                            cycleIndex + 1,
                            recentAlbums, starredAlbums, starredArtists, starredTracks,
                            recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx + 1,
                            mixTracks, usedTrackIds, count, mixType,
                            recentTrackIds, browserFuture);
                } else {
                    Log.d(TAG, mixType + " Step STARRED_TRACKS: no starred tracks, skipping");
                    runMixStep(cycleIndex + 1,
                            recentAlbums, starredAlbums, starredArtists, starredTracks,
                            recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                            mixTracks, usedTrackIds, count, mixType,
                            recentTrackIds, browserFuture);
                }
                break;
        }
    }

    /**
     * Fetches tracks from an album, picks one unused track, adds it to the mix,
     * then attempts to fetch one similar song (DISCOVERY_MIX only) before continuing.

     * @param albumId   Album to fetch tracks from
     * @param fromStep  The step that triggered this fetch — used to determine the next step
     * @param mixType   Controls whether similar songs are fetched after each track
     */
    private void fetchTrackThenSimilar(
            String albumId,
            MixStep fromStep,
            int cycleIndex,
            List<AlbumID3> recentAlbums,
            List<AlbumID3> starredAlbums,
            List<ArtistID3> starredArtists,
            List<Child> starredTracks,
            int recentIdx, int starredAlbumIdx, int starredArtistIdx, int starredTracksIdx,
            List<Child> mixTracks, Set<String> usedTrackIds,
            int count, String mixType,
            Set<String> recentTrackIds,
            ListenableFuture<MediaBrowser> browserFuture) {

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(albumId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        String songIdForSimilar = null;

                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getAlbum() != null
                                && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {

                            List<Child> songs = new ArrayList<>(response.body().getSubsonicResponse().getAlbum().getSongs());
                            Collections.shuffle(songs);

                            for (Child candidate : songs) {
                                if (!usedTrackIds.contains(candidate.getId())) {
                                    mixTracks.add(candidate);
                                    usedTrackIds.add(candidate.getId());
                                    songIdForSimilar = candidate.getId();
                                    Log.d(TAG, mixType + " Added " + fromStep + " / track ["
                                            + mixTracks.size() + "/" + count + "] " + candidate.getTitle());
                                    break;
                                }
                            }
                        } else {
                            Log.w(TAG, mixType + " Album " + albumId + " skipped (empty or failed)");
                        }

                        if (mixTracks.size() >= count) {
                            Log.d(TAG, mixType + " complete with " + mixTracks.size() + " tracks, enqueuing");
                            repository.setChildrenMetadata(mixTracks);
                            enqueue(browserFuture, mixTracks, true);
                            isRunning.set(false);
                            return;
                        }

                        // Similar songs only for DISCOVERY_MIX
                        handleSimilarSongs(songIdForSimilar,
                                cycleIndex,
                                recentAlbums, starredAlbums, starredArtists, starredTracks,
                                recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                                mixTracks, usedTrackIds, count, mixType,
                                recentTrackIds, browserFuture);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, mixType + " Album fetch failed for " + albumId + ": " + t.getMessage());
                        runMixStep(cycleIndex,
                                recentAlbums, starredAlbums, starredArtists, starredTracks,
                                recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                                mixTracks, usedTrackIds, count, mixType,
                                recentTrackIds, browserFuture);
                    }
                });
    }

    /**
     * Adds a starred track directly to the mix (no album fetch needed),
     * then attempts to fetch one similar song (DISCOVERY_MIX only) before continuing.
     *
     * @param track     The starred Child track to add directly
     * @param mixType   Controls whether similar songs are fetched after each track
     */
    private void addTrackThenSimilar(
            Child track,
            int cycleIndex,
            List<AlbumID3> recentAlbums,
            List<AlbumID3> starredAlbums,
            List<ArtistID3> starredArtists,
            List<Child> starredTracks,
            int recentIdx, int starredAlbumIdx, int starredArtistIdx, int starredTracksIdx,
            List<Child> mixTracks, Set<String> usedTrackIds,
            int count, String mixType,
            Set<String> recentTrackIds,
            ListenableFuture<MediaBrowser> browserFuture) {

        String songIdForSimilar = null;

        if (!usedTrackIds.contains(track.getId())) {
            mixTracks.add(track);
            usedTrackIds.add(track.getId());
            songIdForSimilar = track.getId();
            Log.d(TAG, mixType + " Added STARRED_TRACKS / track ["
                    + mixTracks.size() + "/" + count + "] " + track.getTitle());
        } else {
            Log.d(TAG, mixType + " STARRED_TRACKS track " + track.getTitle() + " already used, skipping");
        }

        if (mixTracks.size() >= count) {
            Log.d(TAG, mixType + " complete with " + mixTracks.size() + " tracks, enqueuing");
            repository.setChildrenMetadata(mixTracks);
            enqueue(browserFuture, mixTracks, true);
            isRunning.set(false);
            return;
        }

        // Similar songs only for DISCOVERY_MIX
        handleSimilarSongs(songIdForSimilar,
                cycleIndex,
                recentAlbums, starredAlbums, starredArtists, starredTracks,
                recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                mixTracks, usedTrackIds, count, mixType,
                recentTrackIds, browserFuture);
    }

    /**
     * Attempts to fetch one similar song (DISCOVERY_MIX only) and add it to the mix,
     * then continues the mix cycle.
     * Called after each track addition in both fetchTrackThenSimilar and addTrackThenSimilar.
     *
     * @param songIdForSimilar  The song ID to base the similar search on, null if no track was added
     */
    private void handleSimilarSongs(
            String songIdForSimilar,
            int cycleIndex,
            List<AlbumID3> recentAlbums,
            List<AlbumID3> starredAlbums,
            List<ArtistID3> starredArtists,
            List<Child> starredTracks,
            int recentIdx, int starredAlbumIdx, int starredArtistIdx, int starredTracksIdx,
            List<Child> mixTracks, Set<String> usedTrackIds,
            int count, String mixType,
            Set<String> recentTrackIds,
            ListenableFuture<MediaBrowser> browserFuture) {

        if (!mixType.equals(Constants.AA_DISCOVERYMIX_ID) || songIdForSimilar == null) {
            runMixStep(cycleIndex,
                    recentAlbums, starredAlbums, starredArtists, starredTracks,
                    recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                    mixTracks, usedTrackIds, count, mixType,
                    recentTrackIds, browserFuture);
            return;
        }

        final String finalSongId = songIdForSimilar;
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs(finalSongId, 10)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getSimilarSongs() != null
                                && response.body().getSubsonicResponse().getSimilarSongs().getSongs() != null) {

                            List<Child> similar = new ArrayList<>(
                                    response.body().getSubsonicResponse().getSimilarSongs().getSongs());
                            Collections.shuffle(similar);
                            for (Child candidate : similar) {
                                if (!usedTrackIds.contains(candidate.getId())
                                        && !recentTrackIds.contains(candidate.getId())) {
                                    mixTracks.add(candidate);
                                    usedTrackIds.add(candidate.getId());
                                    Log.d(TAG, mixType + " Added similar track ["
                                            + mixTracks.size() + "/" + count + "] " + candidate.getTitle());
                                    break;
                                }
                            }
                        } else {
                            Log.d(TAG, mixType + " No similar songs for song " + finalSongId);
                        }

                        if (mixTracks.size() >= count) {
                            Log.d(TAG, mixType + " complete with " + mixTracks.size() + " tracks, enqueuing");
                            repository.setChildrenMetadata(mixTracks);
                            enqueue(browserFuture, mixTracks, true);
                            isRunning.set(false);
                            return;
                        }

                        runMixStep(cycleIndex,
                                recentAlbums, starredAlbums, starredArtists, starredTracks,
                                recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                                mixTracks, usedTrackIds, count, mixType,
                                recentTrackIds, browserFuture);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, mixType + " Similar songs fetch failed: " + t.getMessage());
                        runMixStep(cycleIndex,
                                recentAlbums, starredAlbums, starredArtists, starredTracks,
                                recentIdx, starredAlbumIdx, starredArtistIdx, starredTracksIdx,
                                mixTracks, usedTrackIds, count, mixType,
                                recentTrackIds, browserFuture);
                    }
                });
    }

    /**
     * Fallback when no context is available — enqueues random songs directly.
     */
    private void fallbackToRandomSongs(
            int count,
            String usedTrackId,
            ListenableFuture<MediaBrowser> browserFuture) {

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(count, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getRandomSongs() != null
                                && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {

                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();
                            // exclude already playing track
                            songs.removeIf(child -> child.getId().equals(usedTrackId));

                            Log.d(TAG, "Fallback random songs: " + songs.size());
                            repository.setChildrenMetadata(songs);
                            enqueue(browserFuture, songs, true);
                        } else {
                            Log.w(TAG, "Fallback random songs failed");
                        }
                        isRunning.set(false);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Fallback random songs network failure: " + t.getMessage());
                        isRunning.set(false);
                    }
                });
    }
}