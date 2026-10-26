package com.eddyizm.tempus.service;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadHelper;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;

import com.eddyizm.tempus.repository.DownloadRepository;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.MusicUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@UnstableApi
public class DownloaderManager {
    private static final String TAG = "DownloaderManager";

    private final Context context;
    private final DataSource.Factory dataSourceFactory;
    private final DownloadIndex downloadIndex;

    private static HashMap<String, Download> downloads;
    // Handed to the service and not yet finished, the map above only learns of a download once it completes.
    private static final Set<String> requested = ConcurrentHashMap.newKeySet();

    public DownloaderManager(Context context, DataSource.Factory dataSourceFactory, DownloadManager downloadManager) {
        this.context = context.getApplicationContext();
        this.dataSourceFactory = dataSourceFactory;

        downloads = new HashMap<>();
        downloadIndex = downloadManager.getDownloadIndex();

        loadDownloads();
    }

    // No renderers factory: every download here is a progressive file, which needs no track
    // selection, while a factory made the helper build a whole renderer set (FFmpeg included) for
    // each song, which is most of the cost of queueing a large batch.
    private DownloadRequest buildDownloadRequest(MediaItem mediaItem) {
        DownloadHelper helper = DownloadHelper.forMediaItem(context, mediaItem, (RenderersFactory) null, dataSourceFactory);
        try {
            return helper
                    .getDownloadRequest(Util.getUtf8Bytes(checkNotNull(mediaItem.mediaId)))
                    .copyWithId(mediaItem.mediaId);
        } finally {
            helper.release();
        }
    }

    public boolean isDownloaded(String mediaId) {
        @Nullable Download download = downloads.get(mediaId);
        return download != null && download.state != Download.STATE_FAILED;
    }

    public boolean isDownloaded(MediaItem mediaItem) {
        return isDownloaded(mediaItem.mediaId);
    }

    public boolean areDownloaded(List<MediaItem> mediaItems) {
        return mediaItems.stream().anyMatch(this::isDownloaded);
    }

    public boolean isRequested(String mediaId) {
        return requested.contains(mediaId);
    }

    /**
     * Marks songs as asked for ahead of {@link #download(List, List)}, for a caller that filters on
     * {@link #isRequested(String)} and then queues off the main thread: a second request arriving in
     * between must not pick the same songs again. A batch that never reaches the service unmarks them.
     */
    public void markRequested(Collection<String> mediaIds) {
        requested.addAll(mediaIds);
    }

    public void unmarkRequested(Collection<String> mediaIds) {
        requested.removeAll(mediaIds);
    }

    private DownloadRequest prepare(MediaItem mediaItem, com.eddyizm.tempus.model.Download download) {
        MusicUtil.applyTranscodedDownloadMetadata(download);
        download.setDownloadUri(mediaItem.requestMetadata.mediaUri.toString());
        // Marked before the send so a completion racing this call cannot leave the id behind.
        DownloadRequest request = buildDownloadRequest(mediaItem);
        requested.add(mediaItem.mediaId);
        insertDatabase(download);
        return request;
    }

    public boolean download(MediaItem mediaItem, com.eddyizm.tempus.model.Download download) {
        DownloadRequest request = prepare(mediaItem, download);
        try {
            DownloadService.sendAddDownload(context, DownloaderService.class, request, false);
        } catch (IllegalStateException e) {
            requested.remove(mediaItem.mediaId);
            deleteDatabase(mediaItem.mediaId);
            Log.w(TAG, "Download service not started for " + mediaItem.mediaId, e);
            return false;
        }
        return true;
    }

    /**
     * Queues a batch with a single service start. The first song goes through the service, which
     * starts it if needed; the rest go straight to the DownloadManager the service hands them to
     * anyway. A start intent per song meant one onStartCommand per song on the main thread. Returns
     * how many were queued: all of them, or none when the service could not be started.
     */
    public int download(List<MediaItem> mediaItems, List<com.eddyizm.tempus.model.Download> downloads) {
        if (mediaItems.isEmpty()) return 0;

        if (!download(mediaItems.get(0), downloads.get(0))) {
            for (MediaItem mediaItem : mediaItems) requested.remove(mediaItem.mediaId);
            return 0;
        }

        List<DownloadRequest> requests = new ArrayList<>();
        for (int counter = 1; counter < mediaItems.size(); counter++) {
            requests.add(prepare(mediaItems.get(counter), downloads.get(counter)));
        }

        if (!requests.isEmpty()) {
            // DownloadManager is only safe to call on its application thread.
            DownloadManager downloadManager = DownloadUtil.getDownloadManager(context);
            new Handler(downloadManager.getApplicationLooper()).post(() -> {
                for (DownloadRequest request : requests) downloadManager.addDownload(request);
            });
        }

        return mediaItems.size();
    }

    public void remove(MediaItem mediaItem, com.eddyizm.tempus.model.Download download) {
        DownloadService.sendRemoveDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem).id, false);
        deleteDatabase(download.getId());
        downloads.remove(download.getId());
    }

    public void remove(List<MediaItem> mediaItems, List<com.eddyizm.tempus.model.Download> downloads) {
        for (int counter = 0; counter < mediaItems.size(); counter++) {
            remove(mediaItems.get(counter), downloads.get(counter));
        }
    }

    public void removeAll() {
        DownloadService.sendRemoveAllDownloads(context, DownloaderService.class, false);
        deleteAllDatabase();
        DownloadUtil.eraseDownloadFolder(context);
    }

    private void loadDownloads() {
        try (DownloadCursor loadedDownloads = downloadIndex.getDownloads()) {
            while (loadedDownloads.moveToNext()) {
                Download download = loadedDownloads.getDownload();
                downloads.put(download.request.id, download);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to query downloads", e);
            return;
        }

        for (com.eddyizm.tempus.model.Download orphan : getDownloadRepository().getFailedDownloads()) {
            if (!downloads.containsKey(orphan.getId()) && !requested.contains(orphan.getId())) {
                getDownloadRepository().deleteFailed(orphan.getId());
            }
        }
    }

    public static String getDownloadNotificationMessage(String id) {
        com.eddyizm.tempus.model.Download download = getDownloadRepository().getDownload(id);
        return download != null ? download.getTitle() : null;
    }

    public static void updateRequestDownload(Download download) {
        updateDatabase(download.request.id);
        downloads.put(download.request.id, download);
        requested.remove(download.request.id);
    }

    public static void removeRequestDownload(Download download) {
        deleteDatabase(download.request.id);
        downloads.remove(download.request.id);
        requested.remove(download.request.id);
    }

    public static void failRequestDownload(Download download) {
        if (download == null) return;
        getDownloadRepository().deleteFailed(download.request.id);
        downloads.remove(download.request.id);
        requested.remove(download.request.id);
    }

    public static void forgetRequest(Download download) {
        requested.remove(download.request.id);
    }

    private static DownloadRepository getDownloadRepository() {
        return new DownloadRepository();
    }

    private static void insertDatabase(com.eddyizm.tempus.model.Download download) {
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