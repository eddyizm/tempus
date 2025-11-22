package com.cappielloantonio.tempo.service;

import android.content.ComponentName;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.interfaces.MediaIndexCallback;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.repository.ChronologyRepository;
import com.cappielloantonio.tempo.repository.QueueRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MediaManager {
    private static final String TAG = "MediaManager";
    private static WeakReference<MediaBrowser> attachedBrowserRef = new WeakReference<>(null);
    public static AtomicBoolean justStarted = new AtomicBoolean(false);

    public static void runWithBrowser(ListenableFuture<MediaBrowser> future, Consumer<MediaBrowser> callback) {
        if (future == null) return;
        Futures.addCallback(future, new FutureCallback<MediaBrowser>() {
            @Override
            public void onSuccess(MediaBrowser result) {
                callback.accept(result);
            }

            @Override
            public void onFailure(@NonNull Throwable thrown) {
                Log.e(TAG, "Failed to get MediaBrowser instance", thrown);
            }
        }, MoreExecutors.directExecutor());
    }

    public static void registerPlaybackObserver(
            ListenableFuture<MediaBrowser> browserFuture,
            PlaybackViewModel playbackViewModel
    ) {
        runWithBrowser(browserFuture, browser -> {
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
                attachedBrowserRef = new WeakReference<>(browser);
            }
            String mediaId = browser.getCurrentMediaItem() != null
                    ? browser.getCurrentMediaItem().mediaId
                    : null;
            boolean playing = browser.getPlaybackState() == Player.STATE_READY && browser.getPlayWhenReady();
            playbackViewModel.update(mediaId, playing);
        });
    }

    public static void onBrowserReleased(@Nullable MediaBrowser released) {
        MediaBrowser attached = attachedBrowserRef.get();
        if (attached == released) {
            attachedBrowserRef.clear();
        }
    }

    public static void reset(ListenableFuture<MediaBrowser> future) {
        runWithBrowser(future, browser -> {
            if (browser.isPlaying())
                browser.pause();
            browser.stop();
            browser.clearMediaItems();
            clearDatabase();
        });
    }

    public static void hide(ListenableFuture<MediaBrowser> future) {
        runWithBrowser(future, browser -> {
            if (browser.isPlaying())
                browser.pause();
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void check(ListenableFuture<MediaBrowser> future, CompositeDisposable composite) {
        runWithBrowser(future, browser -> {
            QueueRepository queueRepo = getQueueRepository();
            Maybe<Pair<Pair<Integer, Long>, List<Queue>>> maybe =
                    ((browser.getMediaItemCount() == 0) ?
                            Maybe.zip(Maybe.zip(queueRepo.getLastPlayedMediaIndex(), queueRepo.getLastPlayedMediaTimestamp(), Pair::create),
                                            queueRepo.getQueue(),
                                            Pair::create)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                            : Maybe.empty());
            Disposable disposable = maybe.subscribe(input -> {
                        int index = input.first.first;
                        long timestamp = input.first.second;
                        List<Child> media = input.second.stream().map(Child.class::cast).collect(Collectors.toList());
                        browser.clearMediaItems();
                        browser.setMediaItems(MappingUtil.mapMediaItems(media));
                        browser.seekTo(index, timestamp);
                        browser.prepare();
                    });
            composite.add(disposable);
        });
    }

    public static void startQueue(ListenableFuture<MediaBrowser> future, List<Child> media, int startIndex) {
        runWithBrowser(future, browser -> {
            justStarted.set(true);
            browser.clearMediaItems();
            browser.setMediaItems(MappingUtil.mapMediaItems(media), startIndex, 0);
            browser.prepare();
            browser.play();
            enqueueDatabase(media, true, 0);
        });
    }

    public static void startQueue(ListenableFuture<MediaBrowser> future, Child media) {
        runWithBrowser(future, browser -> {
            justStarted.set(true);
            browser.setMediaItem(MappingUtil.mapMediaItem(media));
            browser.prepare();
            browser.play();
            enqueueDatabase(media, true, 0);
        });
    }

    public static void playDownloadedMediaItem(ListenableFuture<MediaBrowser> future, MediaItem mediaItem) {
        runWithBrowser(future, browser -> {
            justStarted.set(true);
            browser.setMediaItem(mediaItem);
            browser.prepare();
            browser.play();
            clearDatabase();
        });
    }

    public static void startRadio(ListenableFuture<MediaBrowser> future, InternetRadioStation internetRadioStation) {
        runWithBrowser(future, browser -> {
            browser.setMediaItem(MappingUtil.mapInternetRadioStation(internetRadioStation));
            browser.prepare();
            browser.play();
        });
    }

    public static void startPodcast(ListenableFuture<MediaBrowser> future, PodcastEpisode podcastEpisode) {
        runWithBrowser(future, browser -> {
            browser.setMediaItem(MappingUtil.mapMediaItem(podcastEpisode));
            browser.prepare();
            browser.play();
        });
    }

    public static void enqueue(ListenableFuture<MediaBrowser> future, List<Child> media, boolean playImmediatelyAfter) {
        runWithBrowser(future, browser -> {
            if (playImmediatelyAfter && browser.getNextMediaItemIndex() != C.INDEX_UNSET) {
                enqueueDatabase(media, false, browser.getNextMediaItemIndex());
                browser.addMediaItems(browser.getNextMediaItemIndex(), MappingUtil.mapMediaItems(media));
            } else {
                enqueueDatabase(media, false, browser.getMediaItemCount());
                browser.addMediaItems(MappingUtil.mapMediaItems(media));
            }
        });
    }

    public static void enqueue(ListenableFuture<MediaBrowser> future, Child media, boolean playImmediatelyAfter) {
        runWithBrowser(future, browser -> {
            if (playImmediatelyAfter && browser.getNextMediaItemIndex() != C.INDEX_UNSET) {
                enqueueDatabase(media, false, browser.getNextMediaItemIndex());
                browser.addMediaItem(browser.getNextMediaItemIndex(), MappingUtil.mapMediaItem(media));
            } else {
                enqueueDatabase(media, false, browser.getMediaItemCount());
                browser.addMediaItem(MappingUtil.mapMediaItem(media));
            }
        });
    }

    public static void shuffle(ListenableFuture<MediaBrowser> future, List<Child> media, int startIndex, int endIndex) {
        runWithBrowser(future, browser -> {
            browser.removeMediaItems(startIndex, endIndex + 1);
            browser.addMediaItems(MappingUtil.mapMediaItems(media).subList(startIndex, endIndex + 1));
            swapDatabase(media);
        });
    }

    public static void swap(ListenableFuture<MediaBrowser> future, List<Child> media, int from, int to) {
        runWithBrowser(future, browser -> {
            browser.moveMediaItem(from, to);
            swapDatabase(media);
        });
    }

    public static void remove(ListenableFuture<MediaBrowser> future, List<Child> media, int toRemove) {
        runWithBrowser(future, browser -> {
            if (browser.getMediaItemCount() > 1 && browser.getCurrentMediaItemIndex() != toRemove) {
                browser.removeMediaItem(toRemove);
                removeDatabase(media, toRemove);
            } else {
                removeDatabase(media, -1);
            }
        });
    }

    public static void removeRange(ListenableFuture<MediaBrowser> future, List<Child> media, int fromItem, int toItem) {
        runWithBrowser(future, browser -> {
            browser.removeMediaItems(fromItem, toItem);
            removeRangeDatabase(media, fromItem, toItem);
        });
    }

    public static void getCurrentIndex(ListenableFuture<MediaBrowser> future, MediaIndexCallback callback) {
        runWithBrowser(future, browser -> {
            callback.onRecovery(browser.getCurrentMediaItemIndex());
        });
    }

    public static void setLastPlayedTimestamp(MediaItem mediaItem) {
        if (mediaItem != null) getQueueRepository().setLastPlayedTimestamp(mediaItem.mediaId);
    }

    public static void setPlayingPausedTimestamp(MediaItem mediaItem, long ms) {
        if (mediaItem != null)
            getQueueRepository().setPlayingPausedTimestamp(mediaItem.mediaId, ms);
    }

    public static void scrobble(MediaItem mediaItem, boolean submission) {
        if (mediaItem != null && Preferences.isScrobblingEnabled()) {
            getSongRepository().scrobble(mediaItem.mediaMetadata.extras.getString("id"), submission);
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    public static void continuousPlay(MediaItem mediaItem) {
        if (mediaItem != null && Preferences.isContinuousPlayEnabled() && Preferences.isInstantMixUsable()) {
            Preferences.setLastInstantMix();

            LiveData<List<Child>> instantMix = getSongRepository().getInstantMix(mediaItem.mediaId, 10);
            instantMix.observeForever(new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> media) {
                    if (media != null) {
                        ListenableFuture<MediaBrowser> mediaBrowserListenableFuture = new MediaBrowser.Builder(
                                App.getContext(),
                                new SessionToken(App.getContext(), new ComponentName(App.getContext(), MediaService.class))
                        ).buildAsync();

                        enqueue(mediaBrowserListenableFuture, media, true);
                    }

                    instantMix.removeObserver(this);
                }
            });
        }
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
