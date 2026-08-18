package com.cappielloantonio.tempo.util;

import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;

import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExternalAudioReader {

    private static final Map<String, DocumentFile> cache = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static final ExecutorService REFRESH_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final MutableLiveData<Long> refreshEvents = new MutableLiveData<>();

    private static volatile String cachedDirUri;
    private static volatile boolean refreshInProgress = false;
    private static volatile boolean refreshQueued = false;

    private static String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\/:*?\\\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }

    private static String normalizeForComparison(String name) {
        String s = sanitizeFileName(name);
        s = Normalizer.normalize(s, Normalizer.Form.NFKD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return s.toLowerCase(Locale.ROOT);
    }

    private static void ensureCache() {
        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            synchronized (LOCK) {
                cache.clear();
                cachedDirUri = null;
            }
            ExternalDownloadMetadataStore.clear();
            return;
        }

        if (uriString.equals(cachedDirUri)) {
            return;
        }

        boolean runSynchronously = false;
        synchronized (LOCK) {
            if (refreshInProgress) {
                return;
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                scheduleRefreshLocked();
                return;
            }

            refreshInProgress = true;
            runSynchronously = true;
        }

        if (runSynchronously) {
            try {
                rebuildCache();
            } finally {
                onRefreshFinished();
            }
        }
    }

    public static void refreshCache() {
        refreshCacheAsync();
    }

    public static void refreshCacheAsync() {
        synchronized (LOCK) {
            cachedDirUri = null;
            cache.clear();
        }
        requestRefresh();
    }

    public static LiveData<Long> getRefreshEvents() {
        return refreshEvents;
    }

    private static String buildKey(String artist, String title, String album) {
        String name = artist != null && !artist.isEmpty() ? artist + " - " + title : title;
        if (album != null && !album.isEmpty()) name += " (" + album + ")";
        return normalizeForComparison(name);
    }

    private static Uri findUri(String artist, String title, String album) {
        ensureCache();
        if (cachedDirUri == null) return null;

        DocumentFile file = cache.get(buildKey(artist, title, album));
        return file != null && file.exists() ? file.getUri() : null;
    }

    public static Uri getUri(Child media) {
        return findUri(media.getArtist(), media.getTitle(), media.getAlbum());
    }

    public static Uri getUri(PodcastEpisode episode) {
        return findUri(episode.getArtist(), episode.getTitle(), episode.getAlbum());
    }

    public static synchronized void removeMetadata(Child media) {
        if (media == null) {
            return;
        }

        String key = buildKey(media.getArtist(), media.getTitle(), media.getAlbum());
        cache.remove(key);
        ExternalDownloadMetadataStore.remove(key);
    }

    public static boolean delete(Child media) {
        ensureCache();
        if (cachedDirUri == null) return false;

        String key = buildKey(media.getArtist(), media.getTitle(), media.getAlbum());
        DocumentFile file = cache.get(key);
        boolean deleted = false;
        if (file != null && file.exists()) {
            deleted = file.delete();
        }
        if (deleted) {
            cache.remove(key);
            ExternalDownloadMetadataStore.remove(key);
        }
        return deleted;
    }

    private static void requestRefresh() {
        synchronized (LOCK) {
            scheduleRefreshLocked();
        }
    }

    private static void scheduleRefreshLocked() {
        if (refreshInProgress) {
            refreshQueued = true;
            return;
        }

        refreshInProgress = true;
        REFRESH_EXECUTOR.execute(() -> {
            try {
                rebuildCache();
            } finally {
                onRefreshFinished();
            }
        });
    }

    private static void rebuildCache() {
        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            synchronized (LOCK) {
                cache.clear();
                cachedDirUri = null;
            }
            ExternalDownloadMetadataStore.clear();
            return;
        }

        DocumentFile directory = DocumentFile.fromTreeUri(App.getContext(), Uri.parse(uriString));
        Map<String, Long> expectedSizes = ExternalDownloadMetadataStore.snapshot();
        Set<String> verifiedKeys = new HashSet<>();
        Map<String, DocumentFile> newEntries = new HashMap<>();

        if (directory != null && directory.canRead()) {
            for (DocumentFile file : directory.listFiles()) {
                if (file == null || file.isDirectory()) continue;
                String existing = file.getName();
                if (existing == null) continue;

                String base = existing.replaceFirst("\\.[^\\.]+$", "");
                String key = normalizeForComparison(base);
                Long expected = expectedSizes.get(key);
                long actualLength = file.length();

                if (expected != null && expected > 0 && actualLength == expected) {
                    newEntries.put(key, file);
                    verifiedKeys.add(key);
                } else {
                    ExternalDownloadMetadataStore.remove(key);
                }
            }
        }

        if (!expectedSizes.isEmpty()) {
            if (verifiedKeys.isEmpty()) {
                ExternalDownloadMetadataStore.clear();
            } else {
                for (String key : expectedSizes.keySet()) {
                    if (!verifiedKeys.contains(key)) {
                        ExternalDownloadMetadataStore.remove(key);
                    }
                }
            }
        }

        synchronized (LOCK) {
            cache.clear();
            cache.putAll(newEntries);
            cachedDirUri = uriString;
        }
    }

    private static void onRefreshFinished() {
        boolean runAgain;
        synchronized (LOCK) {
            refreshInProgress = false;
            runAgain = refreshQueued;
            refreshQueued = false;
        }

        refreshEvents.postValue(SystemClock.elapsedRealtime());

        if (runAgain) {
            requestRefresh();
        }
    }
}