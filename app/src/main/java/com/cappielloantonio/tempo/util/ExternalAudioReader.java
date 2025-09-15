package com.cappielloantonio.tempo.util;

import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ExternalAudioReader {

    private static final Map<String, DocumentFile> cache = new HashMap<>();
    private static String cachedDirUri;

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

    private static synchronized void ensureCache() {
        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            cache.clear();
            cachedDirUri = null;
            return;
        }

        if (uriString.equals(cachedDirUri)) return;

        cache.clear();
        DocumentFile directory = DocumentFile.fromTreeUri(App.getContext(), Uri.parse(uriString));
        if (directory != null && directory.canRead()) {
            for (DocumentFile file : directory.listFiles()) {
                String existing = file.getName();
                if (existing != null) {
                    String base = existing.replaceFirst("\\.[^\\.]+$", "");
                    cache.put(normalizeForComparison(base), file);
                }
            }
        }

        cachedDirUri = uriString;
    }

    /** Rebuilds the cache on next access. */
    public static synchronized void refreshCache() {
        cachedDirUri = null;
        cache.clear();
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
        }
        return deleted;
    }
}