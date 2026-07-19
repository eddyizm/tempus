package com.cappielloantonio.tempo.service;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadHelper;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;

import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.service.DownloaderService;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.Preferences;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@UnstableApi
public class DownloaderManager {
    private static final String TAG = "DownloaderManager";

    private final Context context;
    private final DataSource.Factory dataSourceFactory;
    private final DownloadIndex downloadIndex;

    private static HashMap<String, Download> downloads;

    /**
     * In-memory cache of download metadata (title/artist/album) keyed by the
     * same media id Media3 uses for the DownloadRequest. Populated at enqueue
     * time so the notification can resolve the title of the actively
     * transferring track without a racy / filtered Room DB lookup. This is the
     * single source of truth that keeps the notification title in sync with
     * what Media3 is actually downloading.
     */
    static Map<String, com.cappielloantonio.tempo.model.Download> metadataCache = new ConcurrentHashMap<>();

    /**
     * Test seam: lets unit tests seed the metadata cache without going through
     * the Media3 download path.
     */
    static void setMetadataCache(Map<String, com.cappielloantonio.tempo.model.Download> cache) {
        metadataCache = cache;
    }

    public DownloaderManager(Context context, DataSource.Factory dataSourceFactory, DownloadManager downloadManager) {
        this.context = context.getApplicationContext();
        this.dataSourceFactory = dataSourceFactory;

        downloads = new HashMap<>();
        downloadIndex = downloadManager.getDownloadIndex();

        loadDownloads();
    }

    private DownloadRequest buildDownloadRequest(MediaItem mediaItem) {
        return DownloadHelper
                .forMediaItem(
                        context,
                        mediaItem,
                        DownloadUtil.buildRenderersFactory(context, false),
                        dataSourceFactory)
                .getDownloadRequest(Util.getUtf8Bytes(checkNotNull(mediaItem.mediaId)))
                .copyWithId(mediaItem.mediaId);
    }

    public boolean isDownloaded(String mediaId) {
        @Nullable Download download = downloads.get(mediaId);
        boolean inIndexNotFailed = download != null && download.state != Download.STATE_FAILED;

        // The Media3 in-memory index is a weak signal: it is reconstructed from
        // Media3's persistent download index at startup and is NOT purged when a
        // track is deleted through the external-directory path (the .exo cache
        // index lives in Media3, not in our Room table). A stale "completed"
        // entry there would otherwise make a re-download of a deleted track
        // silently no-op. The Room table is the authoritative record of what we
        // actually downloaded, so require a DB row to agree with the index.
        com.cappielloantonio.tempo.model.Download dbRow = inIndexNotFailed ? getDownloadRepository().getDownloadById(mediaId) : null;
        boolean result = dbRow != null;

        Log.d(TAG, "isDownloaded(" + mediaId + ") -> " + result
                + " | inIndex=" + (download != null)
                + " | state=" + (download != null ? download.state : "n/a")
                + " | dbRow=" + (dbRow != null)
                + " | indexSize=" + (downloads != null ? downloads.size() : "null"));
        return result;
    }

    public boolean isDownloaded(MediaItem mediaItem) {
        return isDownloaded(mediaItem.mediaId);
    }

    public boolean areDownloaded(List<MediaItem> mediaItems) {
        return mediaItems.stream().anyMatch(this::isDownloaded);
    }

    public void download(MediaItem mediaItem, com.cappielloantonio.tempo.model.Download download, boolean forceResume) {
        String externalUri = Preferences.getDownloadDirectoryUri();
        Log.d(TAG, "download() enter mediaId=" + mediaItem.mediaId
                + " | externalUri=" + externalUri
                + " | forceResume=" + forceResume
                + " | hasFailed=" + hasFailedDownloads());

        if (externalUri != null) {
            // For external downloads, set download_uri to the expected exported file URI
            // so that DeleteDownloadStorageDialog can match files for deletion.
            String artist = download.getArtist() != null ? download.getArtist() : "";
            String title = download.getTitle() != null ? download.getTitle() : download.getId();
            String album = download.getAlbum() != null ? download.getAlbum() : "";
            String baseName = artist.isEmpty() ? title : artist + " - " + title;
            if (!album.isEmpty()) baseName += " (" + album + ")";
            String sanitized = baseName.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
            if (sanitized.isEmpty()) sanitized = "download";
            String extension = download.getSuffix();
            if (extension == null || extension.isEmpty()) extension = "mp3";
            download.setDownloadUri(externalUri + "/" + sanitized + "." + extension);

            com.cappielloantonio.tempo.util.ExternalDownloadMetadataStore.recordExportTarget(mediaItem.mediaId, externalUri);
        } else {
            // For internal downloads, set download_uri to the HTTP streaming URI
            // (internal .exo files are managed by Media3's DownloadManager cache)
            download.setDownloadUri(mediaItem.requestMetadata.mediaUri.toString());
        }

        if (isDownloaded(mediaItem) && !hasFailedDownloads()) {
            return;
        }

        // If forceResume is true, force a fresh download from queued state
        if (forceResume) {
            DownloadService.sendRemoveDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem).id, false);
            insertDatabase(download);
            DownloadService.sendAddDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem), false);
        } else {
            // Just add the download - Media3 will manage the queue and persistence
            DownloadService.sendAddDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem), false);
            insertDatabase(download);
        }

        // Cache metadata keyed by the same id Media3 uses so the notification
        // can resolve the current track title without a racy Room lookup.
        metadataCache.put(mediaItem.mediaId, download);
    }

    public void download(List<MediaItem> mediaItems, List<com.cappielloantonio.tempo.model.Download> downloads) {
        for (int counter = 0; counter < mediaItems.size(); counter++) {
            if (!isDownloaded(mediaItems.get(counter)) || hasFailedDownloads()) {
                download(mediaItems.get(counter), downloads.get(counter));
            }
        }
    }

    public void remove(MediaItem mediaItem, com.cappielloantonio.tempo.model.Download download) {
        DownloadService.sendRemoveDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem).id, false);
        deleteDatabase(download.getId());
        downloads.remove(download.getId());
        metadataCache.remove(mediaItem.mediaId);
    }

    public void remove(List<MediaItem> mediaItems, List<com.cappielloantonio.tempo.model.Download> downloads) {
        for (int counter = 0; counter < mediaItems.size(); counter++) {
            remove(mediaItems.get(counter), downloads.get(counter));
        }
    }

    public void removeAll() {
        DownloadService.sendRemoveAllDownloads(context, DownloaderService.class, false);
        deleteAllDatabase();
        DownloadUtil.eraseDownloadFolder(context);
        metadataCache.clear();
    }

    private void loadDownloads() {
        try (DownloadCursor loadedDownloads = downloadIndex.getDownloads()) {
            while (loadedDownloads.moveToNext()) {
                Download download = loadedDownloads.getDownload();
                downloads.put(download.request.id, download);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to query downloads", e);
        }
    }

    public static String getDownloadNotificationMessage(String id) {
        // Prefer the in-memory metadata captured at enqueue time: it is keyed
        // by the same id Media3 uses and is always in sync with the active
        // download, avoiding the disconnected/racy Room lookup that previously
        // caused the notification to show a pending track's title.
        com.cappielloantonio.tempo.model.Download download = metadataCache.get(id);
        if (download == null) {
            download = getDownloadRepository().getDownloadById(id);
        }
        if (download == null) return null;

        // Display format: "Filename.EXT" so the user can see what file
        // is currently being downloaded, kept in sync with the exported filename.
        String artist = download.getArtist();
        String fileName = getDisplayFileName(download);

        if (fileName != null) {
            return fileName;
        }
        if (artist != null && !artist.isEmpty()) {
            return artist;
        }
        if (download.getTitle() != null && !download.getTitle().isEmpty()) {
            return download.getTitle();
        }
        return null;
    }

    private static final String INTERNAL_DOWNLOAD_URI_MARKER = "stream";

    /**
     * Derives the human-readable filename shown in the notification. For
     * user-directory (external) downloads this is the sanitized
     * "Artist - Title (Album).ext" the file is actually saved as. For internal
     * Media3 cache downloads the URI is a streaming URL (e.g. ".../stream?id=...")
     * whose last path segment is "stream", so we instead build the name from the
     * track title and artist to avoid showing a meaningless "stream" filename.
     */
    private static String getDisplayFileName(com.cappielloantonio.tempo.model.Download download) {
        String uri = download.getDownloadUri();
        if (uri != null && !uri.isEmpty() && !isInternalStreamUri(uri)) {
            int slash = uri.lastIndexOf('/');
            String segment = slash >= 0 ? uri.substring(slash + 1) : uri;
            // Strip query string (stream URIs carry ?u=...&id=... params)
            int q = segment.indexOf('?');
            if (q >= 0) segment = segment.substring(0, q);
            if (!segment.isEmpty()) return segment;
        }
        if (download.getTitle() != null && !download.getTitle().isEmpty()) {
            String ext = download.getSuffix();
            if (ext == null || ext.isEmpty()) ext = "mp3";
            String artist = download.getArtist();
            if (artist != null && !artist.isEmpty()) {
                return artist + " - " + download.getTitle() + "." + ext;
            }
            return download.getTitle() + "." + ext;
        }
        return null;
    }

    /**
     * Internal Media3 cache downloads store a streaming URL whose final path
     * segment ("stream") is not a real filename, so the display name must be
     * derived from the track metadata instead.
     */
    private static boolean isInternalStreamUri(String uri) {
        int slash = uri.lastIndexOf('/');
        String segment = slash >= 0 ? uri.substring(slash + 1) : uri;
        int q = segment.indexOf('?');
        if (q >= 0) segment = segment.substring(0, q);
        return INTERNAL_DOWNLOAD_URI_MARKER.equals(segment);
    }

    public static void updateRequestDownload(Download download) {
        updateDatabase(download.request.id);
        downloads.put(download.request.id, download);
    }

    public static void removeRequestDownload(Download download) {
        deleteDatabase(download.request.id);
        downloads.remove(download.request.id);
        metadataCache.remove(download.request.id);
    }

    private static DownloadRepository getDownloadRepository() {
        return new DownloadRepository();
    }

    private static boolean hasFailedDownloads() {
        if (downloads == null) return false;
        return downloads.values().stream()
            .anyMatch(d -> d.state == Download.STATE_FAILED);
    }

    // For backward compatibility with existing callers
    public void download(MediaItem mediaItem, com.cappielloantonio.tempo.model.Download download) {
        download(mediaItem, download, false);
    }

    private static void insertDatabase(com.cappielloantonio.tempo.model.Download download) {
        getDownloadRepository().insert(download);
    }

    private static void deleteDatabase(String id) {
        getDownloadRepository().delete(id);
    }

    private static void deleteAllDatabase() {
        getDownloadRepository().deleteAll();
    }

    private static void updateDatabase(String id) {
        getDownloadRepository().update(id);
    }
}