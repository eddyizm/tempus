package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.LyricsCache;
import com.cappielloantonio.tempo.model.Queue;
import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.repository.FavoriteRepository;
import com.cappielloantonio.tempo.repository.LyricsRepository;
import com.cappielloantonio.tempo.repository.OpenRepository;
import com.cappielloantonio.tempo.repository.QueueRepository;
import com.cappielloantonio.tempo.repository.SongRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.LyricsList;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;
import com.cappielloantonio.tempo.util.OpenSubsonicExtensionsUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.gson.Gson;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@OptIn(markerClass = UnstableApi.class)
public class PlayerBottomSheetViewModel extends AndroidViewModel {
    private static final String TAG = "PlayerBottomSheetViewModel";

    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;
    private final QueueRepository queueRepository;
    private final FavoriteRepository favoriteRepository;
    private final OpenRepository openRepository;
    private final LyricsRepository lyricsRepository;
    private final MutableLiveData<String> lyricsLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<LyricsList> lyricsListLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> lyricsCachedLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> descriptionLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Child> liveMedia = new MutableLiveData<>(null);
    private final MutableLiveData<AlbumID3> liveAlbum = new MutableLiveData<>(null);
    private final MutableLiveData<ArtistID3> liveArtist = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(null);
    private final Gson gson = new Gson();
    private boolean lyricsSyncState = true;
    private LiveData<LyricsCache> cachedLyricsSource;
    private String currentSongId;
    private final Observer<LyricsCache> cachedLyricsObserver = this::onCachedLyricsChanged;


    public PlayerBottomSheetViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        albumRepository = new AlbumRepository();
        artistRepository = new ArtistRepository();
        queueRepository = new QueueRepository();
        favoriteRepository = new FavoriteRepository();
        openRepository = new OpenRepository();
        lyricsRepository = new LyricsRepository();
    }

    public LiveData<List<Queue>> getQueueSong() {
        return queueRepository.getLiveQueue();
    }

    public void setFavorite(Context context, Child media) {
        if (media != null) {
            if (media.getStarred() != null) {
                if (NetworkUtil.isOffline()) {
                    removeFavoriteOffline(media);
                } else {
                    removeFavoriteOnline(media);
                }
            } else {
                if (NetworkUtil.isOffline()) {
                    setFavoriteOffline(media);
                } else {
                    setFavoriteOnline(context, media);
                }
            }
        }
    }

    private void removeFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, false);
        media.setStarred(null);
    }

    private void removeFavoriteOnline(Child media) {
        favoriteRepository.unstar(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                // media.setStarred(new Date());
                favoriteRepository.starLater(media.getId(), null, null, false);
            }
        });
        media.setStarred(null);
    }

    private void setFavoriteOffline(Child media) {
        favoriteRepository.starLater(media.getId(), null, null, true);
        media.setStarred(new Date());
    }

    private void setFavoriteOnline(Context context, Child media) {
        favoriteRepository.star(media.getId(), null, null, new StarCallback() {
            @Override
            public void onError() {
                // media.setStarred(null);
                favoriteRepository.starLater(media.getId(), null, null, true);
            }
        });

        media.setStarred(new Date());

        if (Preferences.isStarredSyncEnabled() && Preferences.getDownloadDirectoryUri() == null) {
            DownloadUtil.getDownloadTracker(context).download(
                    MappingUtil.mapDownload(media),
                    new Download(media)
            );
        }
    }

     public LiveData<String> getLiveLyrics() {
        return lyricsLiveData;
    }

    public LiveData<LyricsList> getLiveLyricsList() {
        return lyricsListLiveData;
    }

    public void refreshMediaInfo(LifecycleOwner owner, Child media) {
        lyricsLiveData.postValue(null);
        lyricsListLiveData.postValue(null);
        lyricsCachedLiveData.postValue(false);

        clearCachedLyricsObserver();

        String songId = media != null ? media.getId() : currentSongId;

        if (TextUtils.isEmpty(songId) || owner == null) {
            return;
        }

        currentSongId = songId;

        observeCachedLyrics(owner, songId);

        LyricsCache cachedLyrics = lyricsRepository.getLyrics(songId);
        if (cachedLyrics != null) {
            onCachedLyricsChanged(cachedLyrics);
        }

        if (NetworkUtil.isOffline() || media == null) {
            return;
        }

        if (OpenSubsonicExtensionsUtil.isSongLyricsExtensionAvailable()) {
            openRepository.getLyricsBySongId(media.getId()).observe(owner, lyricsList -> {
                lyricsListLiveData.postValue(lyricsList);
                lyricsLiveData.postValue(null);

                if (shouldAutoDownloadLyrics() && hasStructuredLyrics(lyricsList)) {
                    saveLyricsToCache(media, null, lyricsList);
                }
            });
        } else {
            songRepository.getSongLyrics(media).observe(owner, lyrics -> {
                lyricsLiveData.postValue(lyrics);
                lyricsListLiveData.postValue(null);

                if (shouldAutoDownloadLyrics() && !TextUtils.isEmpty(lyrics)) {
                    saveLyricsToCache(media, lyrics, null);
                }
            });
        }
    }

    public LiveData<Child> getLiveMedia() {
        return liveMedia;
    }

    public void setLiveMedia(LifecycleOwner owner, String mediaType, String mediaId) {
        currentSongId = mediaId;

        if (!TextUtils.isEmpty(mediaId)) {
            refreshMediaInfo(owner, null);
        } else {
            clearCachedLyricsObserver();
            lyricsLiveData.postValue(null);
            lyricsListLiveData.postValue(null);
            lyricsCachedLiveData.postValue(false);
        }

        if (mediaType != null) {
            switch (mediaType) {
                case Constants.MEDIA_TYPE_MUSIC:
                    songRepository.getSong(mediaId).observe(owner, liveMedia::postValue);
                    descriptionLiveData.postValue(null);
                    break;
                case Constants.MEDIA_TYPE_PODCAST:
                    liveMedia.postValue(null);
                    break;
                default:
                    liveMedia.postValue(null);
                    break;
            }
        } else {
            liveMedia.postValue(null);
        }
    }

    public LiveData<AlbumID3> getLiveAlbum() {
        return liveAlbum;
    }

    public void setLiveAlbum(LifecycleOwner owner, String mediaType, String AlbumId) {
        if (mediaType != null) {
            switch (mediaType) {
                case Constants.MEDIA_TYPE_MUSIC:
                    albumRepository.getAlbum(AlbumId).observe(owner, liveAlbum::postValue);
                    break;
                case Constants.MEDIA_TYPE_PODCAST:
                    liveAlbum.postValue(null);
                    break;
            }
        }
    }

    public LiveData<ArtistID3> getLiveArtist() {
        return liveArtist;
    }

    public void setLiveArtist(LifecycleOwner owner, String mediaType, String ArtistId) {
        if (mediaType != null) {
            switch (mediaType) {
                case Constants.MEDIA_TYPE_MUSIC:
                    artistRepository.getArtist(ArtistId).observe(owner, liveArtist::postValue);
                    break;
                case Constants.MEDIA_TYPE_PODCAST:
                    liveArtist.postValue(null);
                    break;
            }
        }
    }

    public void setLiveDescription(String description) {
        descriptionLiveData.postValue(description);
    }

    public LiveData<String> getLiveDescription() {
        return descriptionLiveData;
    }

    public LiveData<List<Child>> getMediaInstantMix(LifecycleOwner owner, Child media) {
        instantMix.setValue(Collections.emptyList());

        songRepository.getInstantMix(media.getId(), 20).observe(owner, instantMix::postValue);

        return instantMix;
    }

    public LiveData<PlayQueue> getPlayQueue() {
        return queueRepository.getPlayQueue();
    }

    public boolean savePlayQueue() {
        Child media = getLiveMedia().getValue();
        List<Child> queue = queueRepository.getMedia();
        List<String> ids = queue.stream().map(Child::getId).collect(Collectors.toList());

        if (media != null) {
            queueRepository.savePlayQueue(ids, media.getId(), 0);
            return true;
        }

        return false;
    }

    private void observeCachedLyrics(LifecycleOwner owner, String songId) {
        if (TextUtils.isEmpty(songId)) {
            return;
        }

        cachedLyricsSource = lyricsRepository.observeLyrics(songId);
        cachedLyricsSource.observe(owner, cachedLyricsObserver);
    }

    private void clearCachedLyricsObserver() {
        if (cachedLyricsSource != null) {
            cachedLyricsSource.removeObserver(cachedLyricsObserver);
            cachedLyricsSource = null;
        }
    }

    private void onCachedLyricsChanged(LyricsCache lyricsCache) {
        if (lyricsCache == null) {
            lyricsCachedLiveData.postValue(false);
            return;
        }

        lyricsCachedLiveData.postValue(true);

        if (!TextUtils.isEmpty(lyricsCache.getStructuredLyrics())) {
            try {
                LyricsList cachedList = gson.fromJson(lyricsCache.getStructuredLyrics(), LyricsList.class);
                lyricsListLiveData.postValue(cachedList);
                lyricsLiveData.postValue(null);
            } catch (Exception exception) {
                lyricsListLiveData.postValue(null);
                lyricsLiveData.postValue(lyricsCache.getLyrics());
            }
        } else {
            lyricsListLiveData.postValue(null);
            lyricsLiveData.postValue(lyricsCache.getLyrics());
        }
    }

    private void saveLyricsToCache(Child media, String lyrics, LyricsList lyricsList) {
        if (media == null) {
            return;
        }

        if ((lyricsList == null || !hasStructuredLyrics(lyricsList)) && TextUtils.isEmpty(lyrics)) {
            return;
        }

        LyricsCache lyricsCache = new LyricsCache(media.getId());
        lyricsCache.setArtist(media.getArtist());
        lyricsCache.setTitle(media.getTitle());
        lyricsCache.setUpdatedAt(System.currentTimeMillis());

        if (lyricsList != null && hasStructuredLyrics(lyricsList)) {
            lyricsCache.setStructuredLyrics(gson.toJson(lyricsList));
            lyricsCache.setLyrics(null);
        } else {
            lyricsCache.setLyrics(lyrics);
            lyricsCache.setStructuredLyrics(null);
        }

        lyricsRepository.insert(lyricsCache);
        lyricsCachedLiveData.postValue(true);
    }

    private boolean hasStructuredLyrics(LyricsList lyricsList) {
        return lyricsList != null
                && lyricsList.getStructuredLyrics() != null
                && !lyricsList.getStructuredLyrics().isEmpty()
                && lyricsList.getStructuredLyrics().get(0) != null
                && lyricsList.getStructuredLyrics().get(0).getLine() != null
                && !lyricsList.getStructuredLyrics().get(0).getLine().isEmpty();
    }

    private boolean shouldAutoDownloadLyrics() {
        return Preferences.isAutoDownloadLyricsEnabled();
    }

    public boolean downloadCurrentLyrics() {
        Child media = getLiveMedia().getValue();
        if (media == null) {
            return false;
        }

        LyricsList lyricsList = lyricsListLiveData.getValue();
        String lyrics = lyricsLiveData.getValue();

        if ((lyricsList == null || !hasStructuredLyrics(lyricsList)) && TextUtils.isEmpty(lyrics)) {
            return false;
        }

        saveLyricsToCache(media, lyrics, lyricsList);
        return true;
    }

    public LiveData<Boolean> getLyricsCachedState() {
        return lyricsCachedLiveData;
    }

    public void changeSyncLyricsState() {
        lyricsSyncState = !lyricsSyncState;
    }

    public boolean getSyncLyricsState() {
        return lyricsSyncState;
    }
}
