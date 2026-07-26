package com.cappielloantonio.tempo.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.media3.exoplayer.scheduler.Scheduler;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.DownloadUtil;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@UnstableApi
public class DownloaderService extends androidx.media3.exoplayer.offline.DownloadService {

    private static final int JOB_ID = 1;

    // Foreground service notification — managed by Media3, must stay ID 1
    private static final int FOREGROUND_NOTIFICATION_ID = 1;

    // Persistent completion/failure notification — fixed ID so updates replace the same one
    static final int TERMINAL_NOTIFICATION_ID = 2;

    // Shared speed tracking — updated by TerminalStateNotificationHelper, read by getForegroundNotification
    static volatile float currentSpeedBytesPerSec = 0f;

    // Stable batch counters — updated by TerminalStateNotificationHelper, read by getForegroundNotification
    static volatile int batchMaxTotal = 0;
    static volatile int batchCompletedCount = 0;

    // Cache: mediaId → track title, populated lazily in TerminalStateNotificationHelper
    private static final ConcurrentHashMap<String, String> trackTitlesCache = new ConcurrentHashMap<>();

    public DownloaderService() {
        super(
                FOREGROUND_NOTIFICATION_ID,
                DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
                DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                R.string.exo_download_notification_channel_name,
                0
        );
    }

    @NonNull
    @Override
    protected DownloadManager getDownloadManager() {
        DownloadManager downloadManager = DownloadUtil.getDownloadManager(this);
        downloadManager.addListener(new TerminalStateNotificationHelper(this));
        return downloadManager;
    }

    @NonNull
    @Override
    protected Scheduler getScheduler() {
        return new PlatformScheduler(this, JOB_ID);
    }

    /**
     * Overrides the foreground (progress) notification to show "Downloading TrackName" with
     * "X of N • speed" and stable batch counters that don't shrink as Media3 removes completed
     * downloads from its active list (see bug #11).
     *
     * <p>Called by Media3 on the service thread each time the download queue changes.
     * {@code downloads} is the current Media3 snapshot; we prefer static batch counters
     * ({@link #batchMaxTotal}, {@link #batchCompletedCount}) maintained by
     * {@link TerminalStateNotificationHelper} for accurate "X of N" across removal events.
     */
    @NonNull
    @Override
    protected Notification getForegroundNotification(
            @NonNull List<Download> downloads,
            @Requirements.RequirementFlags int notMetRequirements) {

        int total = Math.max(batchMaxTotal, downloads.size());
        int completed = batchCompletedCount;

        // Find the currently downloading (or next queued) track title from cache
        String currentTrack = null;
        for (Download d : downloads) {
            if (d.state == Download.STATE_DOWNLOADING) {
                currentTrack = trackTitlesCache.get(d.request.id);
                break;
            }
        }
        if (currentTrack == null) {
            for (Download d : downloads) {
                if (d.state != Download.STATE_COMPLETED) {
                    currentTrack = trackTitlesCache.get(d.request.id);
                    if (currentTrack != null) break;
                }
            }
        }

        String contentTitle = currentTrack != null
                ? "Downloading " + currentTrack
                : "Downloading";

        int inProgress = total - completed;

        String contentText;
        if (total <= 1) {
            contentText = currentSpeedBytesPerSec > 0f
                    ? formatSpeed(currentSpeedBytesPerSec)
                    : "Downloading…";
        } else if (inProgress > 0) {
            contentText = (completed + 1) + " of " + total;
            if (currentSpeedBytesPerSec > 0f) {
                contentText += " • " + formatSpeed(currentSpeedBytesPerSec);
            }
        } else {
            contentText = "Finalizing…";
        }

        return new NotificationCompat.Builder(this, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_download)
                .setProgress(total, completed, /* indeterminate= */ false)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .build();
    }

    private static String formatSpeed(float bytesPerSec) {
        if (bytesPerSec >= 1_000_000f) {
            return String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000f);
        } else if (bytesPerSec >= 1_000f) {
            return String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1_000f);
        } else {
            return String.format(Locale.US, "%.0f B/s", bytesPerSec);
        }
    }

    // -------------------------------------------------------------------------
    // Terminal-state listener — consolidates completed/failed into ONE notification
    // -------------------------------------------------------------------------

    private static final class TerminalStateNotificationHelper implements DownloadManager.Listener {

        private final Context context;

        // Counters reset at start of each "batch" (when queue goes from empty → non-empty)
        private final AtomicInteger completedCount = new AtomicInteger(0);
        private final AtomicInteger failedCount = new AtomicInteger(0);

        // Speed tracking
        private long lastBytesTotal = 0;
        private long lastSampleMs = 0;
        private float speedBytesPerSec = 0f;

        TerminalStateNotificationHelper(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void onDownloadChanged(
                @NonNull DownloadManager downloadManager,
                @NonNull Download download,
                @Nullable Exception finalException) {

            switch (download.state) {
                case Download.STATE_DOWNLOADING:
                    updateMaxTotal(downloadManager);
                    updateSpeed(downloadManager);
                    primeTrackTitle(download);
                    return;

                case Download.STATE_QUEUED:
                    updateMaxTotal(downloadManager);
                    primeTrackTitle(download);
                    return;

                case Download.STATE_RESTARTING:
                case Download.STATE_STOPPED:
                    // Not terminal — nothing to do
                    return;

                case Download.STATE_COMPLETED:
                    completedCount.incrementAndGet();
                    DownloaderService.batchCompletedCount = completedCount.get();
                    DownloaderManager.updateRequestDownload(download);
                    break;

                case Download.STATE_FAILED:
                    failedCount.incrementAndGet();
                    DownloaderService.batchCompletedCount = completedCount.get() + failedCount.get();
                    break;

                case Download.STATE_REMOVING:
                    // Handled in onDownloadRemoved
                    return;

                default:
                    return;
            }

            // After a terminal state, check if the entire queue is done
            List<Download> currentDownloads = downloadManager.getCurrentDownloads();
            boolean queueEmpty = currentDownloads.stream().noneMatch(
                    d -> d.state == Download.STATE_QUEUED
                            || d.state == Download.STATE_DOWNLOADING
                            || d.state == Download.STATE_RESTARTING
            );

            if (queueEmpty) {
                postFinalNotification();
                completedCount.set(0);
                failedCount.set(0);
                speedBytesPerSec = 0f;
                DownloaderService.currentSpeedBytesPerSec = 0f;
                DownloaderService.batchMaxTotal = 0;
                DownloaderService.batchCompletedCount = 0;
                DownloaderService.trackTitlesCache.clear();
                lastBytesTotal = 0;
                lastSampleMs = 0;
            }

        }

        @Override
        public void onDownloadRemoved(
                @NonNull DownloadManager downloadManager,
                @NonNull Download download) {
            DownloaderManager.removeRequestDownload(download);
        }

        private void updateSpeed(@NonNull DownloadManager downloadManager) {
            long nowMs = SystemClock.elapsedRealtime();
            long totalBytes = 0;
            for (Download d : downloadManager.getCurrentDownloads()) {
                totalBytes += d.getBytesDownloaded();
            }
            if (lastSampleMs > 0) {
                long elapsed = nowMs - lastSampleMs;
                if (elapsed > 500) {
                    speedBytesPerSec = (totalBytes - lastBytesTotal) * 1000f / elapsed;
                    lastBytesTotal = totalBytes;
                    lastSampleMs = nowMs;
                }
            } else {
                lastBytesTotal = totalBytes;
                lastSampleMs = nowMs;
            }

            DownloaderService.currentSpeedBytesPerSec = speedBytesPerSec;
        }

        /**
         * Updates the stable {@link DownloaderService#batchMaxTotal} so that
         * {@link #getForegroundNotification(List, int)} shows the correct total
         * even after Media3 removes completed items from its active list.
         */
        private void updateMaxTotal(@NonNull DownloadManager downloadManager) {
            int currentSize = downloadManager.getCurrentDownloads().size();
            if (currentSize > DownloaderService.batchMaxTotal) {
                DownloaderService.batchMaxTotal = currentSize;
            }
        }

        /**
         * Resolves a Media3 download ID to a human-readable track title and caches
         * it in {@link DownloaderService#trackTitlesCache} so the progress notification
         * can display which track is currently being downloaded
         *
         * <p>Called on the Media3 listener thread (background), so blocking DB lookups
         * via {@link DownloaderManager#getDownloadNotificationMessage} are acceptable.
         */
        private void primeTrackTitle(Download download) {
            String id = download.request.id;
            if (!DownloaderService.trackTitlesCache.containsKey(id)) {
                String title = DownloaderManager.getDownloadNotificationMessage(id);
                if (title != null) {
                    DownloaderService.trackTitlesCache.put(id, title);
                }
            }
        }

        private void postFinalNotification() {
            int done = completedCount.get();
            int failed = failedCount.get();

            Notification notification;

            if (done > 0 && failed == 0) {
                String msg = done == 1
                        ? "1 track downloaded"
                        : done + " tracks downloaded";
                notification = buildSummaryNotification(
                        "Downloads complete", msg, R.drawable.ic_check_circle);
            } else if (done > 0) {
                String msg = done + " downloaded, " + failed + " failed";
                notification = buildSummaryNotification(
                        "Downloads complete", msg, R.drawable.ic_check_circle);
            } else {
                String msg = failed == 1 ? "1 track failed" : failed + " tracks failed";
                notification = buildSummaryNotification(
                        "Download failed", msg, R.drawable.ic_error);
            }

            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(TERMINAL_NOTIFICATION_ID, notification);
        }

        private Notification buildSummaryNotification(String title, String text, int iconRes) {
            return new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(iconRes)
                    .setOngoing(false)
                    .setAutoCancel(false)   // stays until user swipes it
                    .setSilent(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOnlyAlertOnce(true)
                    .build();
        }
    }
}
