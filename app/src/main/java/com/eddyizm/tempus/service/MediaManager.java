package com.eddyizm.tempus.service;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;

import com.eddyizm.tempus.database.dao.QueueDao;
import com.eddyizm.tempus.interfaces.MediaIndexCallback;
import com.eddyizm.tempus.model.Chronology;
import com.eddyizm.tempus.repository.ChronologyRepository;
import com.eddyizm.tempus.repository.QueueRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.InternetRadioStation;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MappingUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.PlaybackViewModel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MediaManager {
    private static final String TAG = "MediaManager";
    private static WeakReference<MediaBrowser> attachedBrowserRef = new WeakReference<>(null);
    public static AtomicBoolean justStarted = new AtomicBoolean(false);
    public static AtomicBoolean continuousPlayIsRunning = new AtomicBoolean(false);

    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    /**
     * Where a queue edit is applied: the service's own player, or a session controller.
     * The service returns null from livePlayer() once it is destroyed, so an edit that
     * arrives after that is dropped instead of reaching a released player; the check and the
     * edit both run on the app's main thread, which is also where the service is destroyed.
     * A controller always returns itself, which leaves the browser paths exactly as they
     * were: nothing on them was ever guarded by a release check.
     */
    public interface QueueTarget {
        @Nullable
        Player livePlayer();

        void requestPlayNextFixup(int insertPos, int count, int targetCount);
    }

    private static QueueTarget queueTargetFor(MediaBrowser browser) {
        return new QueueTarget() {
            @Override
            public Player livePlayer() {
                return browser;
            }

            @Override
            public void requestPlayNextFixup(int insertPos, int count, int targetCount) {
                Bundle args = new Bundle();
                args.putInt(Constants.PLAY_NEXT_INSERT_POS, insertPos);
                args.putInt(Constants.PLAY_NEXT_COUNT, count);
                args.putInt(Constants.PLAY_NEXT_TARGET_COUNT, targetCount);
                ListenableFuture<SessionResult> fixup = browser.sendCustomCommand(
                        new SessionCommand(Constants.CUSTOM_COMMAND_PLAY_NEXT, Bundle.EMPTY), args);
                Futures.addCallback(fixup, new FutureCallback<SessionResult>() {
                    @Override
                    public void onSuccess(SessionResult result) {
                        if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                            Log.e(TAG, "insertPlayNext: play-next fixup rejected with code " + result.resultCode);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "insertPlayNext: play-next fixup command failed", t);
                    }
                }, MoreExecutors.directExecutor());
            }
        };
    }

    public static void registerPlaybackObserver(
            ListenableFuture<MediaBrowser> browserFuture,
            PlaybackViewModel playbackViewModel
    ) {
        if (browserFuture == null) return;

        Futures.addCallback(browserFuture, new FutureCallback<MediaBrowser>() {
            @Override
            public void onSuccess(MediaBrowser browser) {
                MediaBrowser current = attachedBrowserRef.get();
                if (current != browser) {
                    browser.addListener(new Player.Listener() {
                        @Override
                        public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
                            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                                    || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                                    || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {

                                String mediaId = player.getCurrentMediaItem() != null
                                        ? player.getCurrentMediaItem().mediaId
                                        : null;

                                boolean playing = player.getPlaybackState() == Player.STATE_READY
                                        && player.getPlayWhenReady();

                                playbackViewModel.update(mediaId, playing);
                            }
                        }
                    });

                    String mediaId = browser.getCurrentMediaItem() != null
                            ? browser.getCurrentMediaItem().mediaId
                            : null;
                    boolean playing = browser.getPlaybackState() == Player.STATE_READY && browser.getPlayWhenReady();
                    playbackViewModel.update(mediaId, playing);

                    attachedBrowserRef = new WeakReference<>(browser);
                } else {
                    String mediaId = browser.getCurrentMediaItem() != null
                            ? browser.getCurrentMediaItem().mediaId
                            : null;
                    boolean playing = browser.getPlaybackState() == Player.STATE_READY && browser.getPlayWhenReady();
                    playbackViewModel.update(mediaId, playing);
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "Failed to get MediaBrowser instance", t);
            }
        }, MoreExecutors.directExecutor());
    }

    public static void onBrowserReleased(@Nullable MediaBrowser released) {
        MediaBrowser attached = attachedBrowserRef.get();
        if (attached == released) {
            attachedBrowserRef.clear();
        }
    }

    public static void reset(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (mediaBrowserListenableFuture.get().isPlaying()) {
                            mediaBrowserListenableFuture.get().pause();
                        }

                        mediaBrowserListenableFuture.get().stop();
                        mediaBrowserListenableFuture.get().clearMediaItems();
                        clearDatabase();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void hide(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (mediaBrowserListenableFuture.get().isPlaying()) {
                            mediaBrowserListenableFuture.get().pause();
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void check(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        if (mediaBrowserListenableFuture.get().getMediaItemCount() < 1) {
                            List<Child> media = getQueueRepository().getMedia();
                            if (media != null && media.size() >= 1) {
                                init(mediaBrowserListenableFuture, media);
                            }
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void init(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        final MediaBrowser browser = mediaBrowserListenableFuture.get();

                        backgroundExecutor.execute(() -> {
                            final List<MediaItem> items = MappingUtil.mapMediaItems(media);
                            final int index = getQueueRepository().getLastPlayedMediaIndex();
                            final long position = getQueueRepository().getLastPlayedMediaTimestamp();

                            new Handler(Looper.getMainLooper()).post(() -> {
                                // The user can start something while we map, and check() only
                                // tested this before the mapping began. Do not stomp their pick.
                                if (browser.getMediaItemCount() > 0) return;
                                browser.clearMediaItems();
                                browser.setMediaItems(items);
                                browser.seekTo(index, position);
                                browser.prepare();
                            });
                        });
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex) {
        if (mediaBrowserListenableFuture != null) {

            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        final MediaBrowser browser = mediaBrowserListenableFuture.get();

                        // Map off the caller thread. getUri does a blocking DB lookup per song
                        // (each one spawns a Thread and joins it), and this listener runs via
                        // directExecutor on the click thread, so a large list froze the UI.
                        // Only the player calls go back to main.
                        backgroundExecutor.execute(() -> {
                            final List<MediaItem> items = MappingUtil.mapMediaItems(media);

                            new Handler(Looper.getMainLooper()).post(() -> {
                                justStarted.set(true);
                                browser.setMediaItems(items, startIndex, 0);
                                browser.prepare();

                                Player.Listener timelineListener = new Player.Listener() {
                                    @Override
                                    public void onTimelineChanged(Timeline timeline, int reason) {
                                        int itemCount = browser.getMediaItemCount();
                                        if (itemCount > 0 && startIndex >= 0 && startIndex < itemCount) {
                                            browser.seekTo(startIndex, 0);
                                            browser.play();
                                            browser.removeListener(this);
                                        } else {
                                            Log.d(TAG, "Cannot start playback: itemCount=" + itemCount + ", startIndex=" + startIndex);
                                        }
                                    }
                                };

                                browser.addListener(timelineListener);
                            });

                            enqueueDatabase(media, true, 0);
                        });
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Error in startQueue: " + e.getMessage(), e);
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startQueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        justStarted.set(true);
                        browser.setMediaItem(MappingUtil.mapMediaItem(media));
                        browser.prepare();
                        browser.play();
                        enqueueDatabase(media, true, 0);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void playDownloadedMediaItem(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaItem mediaItem) {
        if (mediaBrowserListenableFuture != null && mediaItem != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                        justStarted.set(true);
                        mediaBrowser.setMediaItem(mediaItem);
                        mediaBrowser.prepare();
                        mediaBrowser.play();
                        clearDatabase();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startRadio(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, InternetRadioStation internetRadioStation) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        justStarted.set(true);
                        browser.setMediaItem(MappingUtil.mapInternetRadioStation(internetRadioStation));
                        browser.prepare();
                        browser.play();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void startPodcast(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, PodcastEpisode podcastEpisode) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        justStarted.set(true);
                        browser.setMediaItem(MappingUtil.mapMediaItem(podcastEpisode));
                        browser.prepare();
                        browser.play();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, boolean playImmediatelyAfter) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        enqueue(queueTargetFor(mediaBrowserListenableFuture.get()), media, playImmediatelyAfter);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void enqueue(QueueTarget queueTarget, List<Child> media, boolean playImmediatelyAfter) {
        Player player = queueTarget.livePlayer();
        if (player == null) {
            Log.d(TAG, "enqueue: queue target gone, dropping " + media.size() + " items");
            return;
        }

        Log.d(TAG, "enqueue");
        int current = player.getCurrentMediaItemIndex();
        if (playImmediatelyAfter && current != C.INDEX_UNSET) {
            enqueueDatabase(media, false, current + 1);
            insertPlayNext(queueTarget, player, MappingUtil.mapMediaItems(media));
        } else {
            enqueueDatabase(media, false, player.getMediaItemCount());
            player.addMediaItems(MappingUtil.mapMediaItems(media));
        }
    }

    public static void enqueue(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, Child media, boolean playImmediatelyAfter) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.e(TAG, "enqueue");
                        MediaBrowser browser = mediaBrowserListenableFuture.get();
                        int current = browser.getCurrentMediaItemIndex();
                        if (playImmediatelyAfter && current != C.INDEX_UNSET) {
                            enqueueDatabase(media, false, current + 1);
                            insertPlayNext(queueTargetFor(browser), browser, Collections.singletonList(MappingUtil.mapMediaItem(media)));
                        } else {
                            enqueueDatabase(media, false, mediaBrowserListenableFuture.get().getMediaItemCount());
                            mediaBrowserListenableFuture.get().addMediaItem(MappingUtil.mapMediaItem(media));
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    // "Play next": insert the items right after the current item on the timeline, then —
    // once the insert has actually applied — ask the service to move them next in the
    // ExoPlayer shuffle order too. The shuffle order fixup must run on the service (only it
    // can setShuffleOrder), and it cannot run until the insert is visible on the timeline
    // (from a controller, addMediaItems updates the controller optimistically before the
    // session's async onAddMediaItems even runs), so the service stashes the request and
    // applies it from its own onTimelineChanged once the target count is reached, otherwise
    // the fixup would be clobbered by addMediaItems' own internal shuffle insert. The fixup
    // is a no-op when shuffle is off.
    private static void insertPlayNext(QueueTarget queueTarget, Player player, List<MediaItem> items) {
        if (items.isEmpty()) return;
        int insertPos = player.getCurrentMediaItemIndex() + 1;
        int targetCount = player.getMediaItemCount() + items.size();
        queueTarget.requestPlayNextFixup(insertPos, items.size(), targetCount);
        player.addMediaItems(insertPos, items);
    }

    public static void shuffle(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int startIndex, int endIndex) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.e(TAG, "shuffle");
                        final MediaBrowser browser = mediaBrowserListenableFuture.get();

                        backgroundExecutor.execute(() -> {
                            final List<MediaItem> mapped = MappingUtil.mapMediaItems(media);

                            new Handler(Looper.getMainLooper()).post(() -> {
                                browser.removeMediaItems(startIndex, endIndex + 1);
                                browser.addMediaItems(mapped.subList(startIndex, endIndex + 1));
                            });

                            swapDatabase(media);
                        });
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void swap(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int from, int to) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.e(TAG, "swap");
                        mediaBrowserListenableFuture.get().moveMediaItem(from, to);
                        swapDatabase(media);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void remove(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int toRemove) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.e(TAG, "remove");
                        if (mediaBrowserListenableFuture.get().getMediaItemCount() > 1 && mediaBrowserListenableFuture.get().getCurrentMediaItemIndex() != toRemove) {
                            mediaBrowserListenableFuture.get().removeMediaItem(toRemove);
                            removeDatabase(media, toRemove);
                        } else {
                            removeDatabase(media, -1);
                        }
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void removeRange(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, List<Child> media, int fromItem, int toItem) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        Log.e(TAG, "remove range");
                        mediaBrowserListenableFuture.get().removeMediaItems(fromItem, toItem);
                        removeRangeDatabase(media, fromItem, toItem);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void removeRange(QueueTarget queueTarget, int fromItem, int toItem) {
        Player player = queueTarget.livePlayer();
        if (player == null) {
            Log.d(TAG, "removeRange: queue target gone");
            return;
        }

        player.removeMediaItems(fromItem, toItem);
        getQueueRepository().deleteRange(fromItem, toItem);
    }

    public static void getCurrentIndex(ListenableFuture<MediaBrowser> mediaBrowserListenableFuture, MediaIndexCallback callback) {
        if (mediaBrowserListenableFuture != null) {
            mediaBrowserListenableFuture.addListener(() -> {
                try {
                    if (mediaBrowserListenableFuture.isDone()) {
                        callback.onRecovery(mediaBrowserListenableFuture.get().getCurrentMediaItemIndex());
                    }
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
    }

    public static void setLastPlayedTimestamp(MediaItem mediaItem) {
        if (mediaItem != null) getQueueRepository().setLastPlayedTimestamp(mediaItem.mediaId);
    }

    public static void setPlayingPausedTimestamp(MediaItem mediaItem, long ms) {
        if (mediaItem != null)
            getQueueRepository().setPlayingPausedTimestamp(mediaItem.mediaId, ms);
    }

    public static void scrobble(MediaItem mediaItem, boolean submission) {
        if (mediaItem != null && mediaItem.mediaMetadata.extras != null && Preferences.isScrobblingEnabled()) {
            getSongRepository().scrobble(mediaItem.mediaMetadata.extras.getString("id"), submission);
        }
    }

    // Last.fm rule: a play counts once it has passed half the track or 4 minutes, whichever comes first; never under 30 seconds.
    public static boolean meetsScrobbleThreshold(long positionMs, long durationMs) {
        if (durationMs <= 30_000L) return false;
        return positionMs >= Math.min(durationMs / 2, 240_000L);
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void continuousPlay(MediaItem mediaItem,
                                      QueueTarget queueTarget) {
        continuousPlay(mediaItem, queueTarget, null);
    }
    @OptIn(markerClass = UnstableApi.class)
    public static void continuousPlay(MediaItem mediaItem,
                                      QueueTarget queueTarget,
                                      @Nullable Runnable onComplete) {
        if (continuousPlayIsRunning.get() || !Preferences.isInstantMixUsable()) {
            Log.d(TAG, "Continuous Play: already running");
            if (onComplete != null) onComplete.run();
            return;
        }
        Player player = queueTarget.livePlayer();
        if (player == null) {
            Log.d(TAG, "Continuous Play: queue target gone");
            if (onComplete != null) onComplete.run();
            return;
        }
        Log.d(TAG, "Continuous Play");

        Preferences.setLastInstantMix();
        continuousPlayIsRunning.set(true);

        // keep only NUMBER_TRACKS_KEEP_IN_QUEUE items in queue before starting continuous play
        int numberOfTracksKeepInQueue = Preferences.getNumberOfTracksKeepInQueue();
        int currentIndex = player.getCurrentMediaItem() != null
                ? player.getCurrentMediaItemIndex()
                : 0;
        int firstToKeep = Math.max(0, currentIndex - numberOfTracksKeepInQueue);
        if (firstToKeep > 0) {
            Log.d(TAG, "Continuous Play: purging " + firstToKeep + " old items from queue");
            removeRange(queueTarget, 0, firstToKeep);
        }
        String trackId = mediaItem.mediaId;
        String artistId = mediaItem.mediaMetadata.extras != null
                ? mediaItem.mediaMetadata.extras.getString("artistId")
                : null;

        LiveData<List<Child>> instantMix =
                getSongRepository().getContinuousMix(trackId, artistId, 25);

        instantMix.observeForever(new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> media) {
                instantMix.removeObserver(this);

                // Filter against current queue before deciding if we need fallback.
                // getSimilarSongs2 doesn't know what's already queued, so it may
                // return tracks we already have. Filter first, then decide.
                if (media != null && !media.isEmpty()) {
                    List<Child> filtered = dedupAgainstQueue(media, queueTarget);
                    if (!filtered.isEmpty()) {
                        Log.d(TAG, "Continuous Play: adding " + filtered.size() + " similar tracks");
                        enqueue(queueTarget, filtered, true);
                        continuousPlayIsRunning.set(false);
                        return;
                    }
                }

                if (Preferences.isFallbackToRandomTracksEnabled()) {
                    Log.w(TAG, "Continuous Play: no new similar tracks, falling back to random songs");
                    LiveData<List<Child>> randomSongs = getSongRepository().getRandomSample(25, null, null);
                    randomSongs.observeForever(new Observer<List<Child>>() {
                        @Override
                        public void onChanged(List<Child> random) {
                            randomSongs.removeObserver(this);
                            if (random != null && !random.isEmpty()) {
                                List<Child> filtered = dedupAgainstQueue(random, queueTarget);
                                if (!filtered.isEmpty()) {
                                    Log.d(TAG, "Continuous Play: adding " + filtered.size() + " random tracks");
                                    enqueue(queueTarget, filtered, true);
                                } else {
                                    Log.w(TAG, "Continuous Play: random tracks already in queue");
                                }
                            } else {
                                Log.w(TAG, "Continuous Play: random fallback also empty");
                            }
                            continuousPlayIsRunning.set(false);
                        }
                    });
                } else {
                    Log.w(TAG, "Continuous Play: no new similar tracks, random fallback disabled");
                    continuousPlayIsRunning.set(false);
                }
            }
        });
    }

    private static List<Child> dedupAgainstQueue(List<Child> candidates,
                                                  QueueTarget queueTarget) {
        Player player = queueTarget.livePlayer();
        if (player == null) return new ArrayList<>(candidates);

        Set<String> currentIds = new HashSet<>();
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            currentIds.add(player.getMediaItemAt(i).mediaId);
        }

        return candidates.stream()
                .filter(child -> !currentIds.contains(child.getId()))
                .collect(Collectors.toList());
    }

    public static void saveChronology(MediaItem mediaItem) {
        if (mediaItem != null) {
            getChronologyRepository().insert(new Chronology(mediaItem));
        }
    }

    private static QueueRepository getQueueRepository() {
        return new QueueRepository();
    }

    private static SongRepository getSongRepository() {
        return new SongRepository();
    }

    private static ChronologyRepository getChronologyRepository() {
        return new ChronologyRepository();
    }

    private static void enqueueDatabase(List<Child> media, boolean reset, int afterIndex) {
        getQueueRepository().insertAll(media, reset, afterIndex);
    }

    private static void enqueueDatabase(Child media, boolean reset, int afterIndex) {
        getQueueRepository().insert(media, reset, afterIndex);
    }

    private static void swapDatabase(List<Child> media) {
        getQueueRepository().insertAll(media, true, 0);
    }

    private static void removeDatabase(List<Child> media, int toRemove) {
        if (toRemove != -1) {
            media.remove(toRemove);
            getQueueRepository().insertAll(media, true, 0);
        }
    }

    private static void removeRangeDatabase(List<Child> media, int fromItem, int toItem) {
        List<Child> toRemove = media.subList(fromItem, toItem);

        media.removeAll(toRemove);

        getQueueRepository().insertAll(media, true, 0);
    }

    public static void clearDatabase() {
        getQueueRepository().deleteAll();
    }
}
