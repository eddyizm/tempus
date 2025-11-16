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
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class DownloaderManager {
    private static final String TAG = "DownloaderManager";

    ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Context context;
    private final DataSource.Factory dataSourceFactory;
    private final DownloadIndex downloadIndex;

    private final static ConcurrentHashMap<String, Download> downloads = new ConcurrentHashMap<>();
    private final static ConcurrentHashMap<String, com.cappielloantonio.tempo.model.Download> downloads2 = new ConcurrentHashMap<>();

    public DownloaderManager(Context context, DataSource.Factory dataSourceFactory, DownloadManager downloadManager) {
        this.context = context.getApplicationContext();
        this.dataSourceFactory = dataSourceFactory;

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
        return download != null && download.state != Download.STATE_FAILED;
    }

    public com.cappielloantonio.tempo.model.Download getDownload(String id) {
        if (!downloads.containsKey(id))
            return null;
        return downloads2.get(id);
    }

    public boolean isDownloaded(MediaItem mediaItem) {
        return isDownloaded(mediaItem.mediaId);
    }

    public boolean areDownloaded(List<MediaItem> mediaItems) {
        return mediaItems.stream().anyMatch(this::isDownloaded);
    }

    private void download(MediaItem mediaItem, com.cappielloantonio.tempo.model.Download download) {
        if (mediaItem.requestMetadata.mediaUri == null) return;
        downloads2.put(download.getId(), download);
        download.setDownloadUri(mediaItem.requestMetadata.mediaUri.toString());

        DownloadService.sendAddDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem), false);
        insertDatabase(download);
    }

    public void download(List<Child> items) {
        executor.submit(() -> {
            for (Child item : items)
                download(MappingUtil.mapDownload(item), new com.cappielloantonio.tempo.model.Download(item));
        });
    }

    public void download(List<Child> items, List<com.cappielloantonio.tempo.model.Download> downloads) {
        executor.submit(() -> {
            for (int counter = 0; counter < items.size(); counter++)
                download(MappingUtil.mapDownload(items.get(counter)), downloads.get(counter));
        });
    }

    private void remove(MediaItem mediaItem, com.cappielloantonio.tempo.model.Download download) {
        DownloadService.sendRemoveDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem).id, false);
        deleteDatabase(download.getId());
        downloads.remove(download.getId());
    }

    public void remove(List<Child> items) {
        executor.submit(() -> {
            for (Child item : items)
                remove(MappingUtil.mapDownload(item), new com.cappielloantonio.tempo.model.Download(item));
        });
    }

    public void removeAll() {
        executor.submit(() -> {
            DownloadService.sendRemoveAllDownloads(context, DownloaderService.class, false);
            deleteAllDatabase();
            DownloadUtil.eraseDownloadFolder(context);
        });
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

    public String getDownloadNotificationMessage(String id) {
        com.cappielloantonio.tempo.model.Download download = getDownloadRepository().getDownload(id);
        return download != null ? download.getTitle() : null;
    }

    public void updateRequestDownload(Download download) {
        executor.submit(() -> {
            updateDatabase(download.request.id);
            downloads.put(download.request.id, download);
        });
    }

    public void removeRequestDownload(Download download) {
        executor.submit(() -> {
            deleteDatabase(download.request.id);
            downloads.remove(download.request.id);
        });
    }

    private DownloadRepository getDownloadRepository() {
        return new DownloadRepository();
    }

    private void insertDatabase(com.cappielloantonio.tempo.model.Download download) {
        getDownloadRepository().insert(download);
    }

    private void deleteDatabase(String id) {
        getDownloadRepository().delete(id);
    }

    private void deleteAllDatabase() {
        getDownloadRepository().deleteAll();
    }

    private void updateDatabase(String id) {
        getDownloadRepository().update(id);
    }
}