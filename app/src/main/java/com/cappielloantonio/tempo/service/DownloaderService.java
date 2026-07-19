package com.cappielloantonio.tempo.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.media3.exoplayer.scheduler.Scheduler;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.broadcast.receiver.DownloadControlReceiver;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Foreground service that drives Media3 DownloadManager.
 * ────────────────────────────────────────────────────────────────────────────
 */
@UnstableApi
public class DownloaderService extends androidx.media3.exoplayer.offline.DownloadService {

    private static final int JOB_ID = 1;
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int PAUSED_NOTIFICATION_ID = 2;
    private static final int COMPLETE_NOTIFICATION_ID = 3;

    private static volatile int batchTotal = 0;

    /**
     * Tracks downloads that have actually reached STATE_COMPLETED via the
     * DownloadManager listener. Deriving "N of M" from a single snapshot of the
     * download index under-counts completed tracks (Media3 runs several
     * downloads in parallel and only refreshes the foreground notification
     * occasionally), which made the counter lag by a few tracks. Counting real
     * terminal transitions keeps it accurate.
     */
    private static final java.util.Set<String> completedDownloadIds =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static volatile boolean listenerRegistered = false;

    private static long lastTotalBytesDownloaded = 0L;
    private static long lastSpeedCheckTimeMs = 0L;
    private static String lastSpeedLabel = "";

    private static volatile boolean isPaused = false;
    public static volatile boolean isCancelling = false;

    private static final int RC_PAUSE   = 10;
    private static final int RC_RESUME  = 11;
    private static final int RC_CANCEL  = 12;

    public DownloaderService() {
        super(
                FOREGROUND_NOTIFICATION_ID,
                DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
                DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                R.string.exo_download_notification_channel_name,
                0);
    }

    @NonNull
    @Override
    protected DownloadManager getDownloadManager() {
        DownloadManager downloadManager = DownloadUtil.getDownloadManager(this);
        if (!listenerRegistered) {
            DownloadNotificationHelper notificationHelper =
                    DownloadUtil.getDownloadNotificationHelper(this);
            downloadManager.addListener(
                    new TerminalStateNotificationHelper(this, notificationHelper));
            listenerRegistered = true;
        }
        return downloadManager;
    }

    @NonNull
    @Override
    protected Scheduler getScheduler() {
        return new PlatformScheduler(this, JOB_ID);
    }


    @NonNull
    @Override
    protected Notification getForegroundNotification(
            @NonNull List<Download> downloads,
            @Requirements.RequirementFlags int notMetRequirements) {

        int activeCount = 0;
        long totalBytesDownloaded = 0L;
        Download activeDownload = null;

        for (Download download : downloads) {
            switch (download.state) {
                case Download.STATE_QUEUED:
                case Download.STATE_RESTARTING:
                case Download.STATE_STOPPED:
                    activeCount++;
                    totalBytesDownloaded += download.getBytesDownloaded();
                    break;
                case Download.STATE_DOWNLOADING:
                    activeCount++;
                    totalBytesDownloaded += download.getBytesDownloaded();
                    if (activeDownload == null) {
                        activeDownload = download;
                    }
                    break;
                default:
                    break;
            }
        }

        if (activeCount > batchTotal) {
            batchTotal = activeCount;
        }

        // Use the listener-tracked completed count (accurate under parallel
        // downloads) rather than deriving it from a single snapshot, which
        // under-counted completed tracks because the foreground notification is
        // only refreshed occasionally.
        int completed = Math.min(completedDownloadIds.size(), batchTotal);

        // ── Step 1.4: Speed calculation ──────────────────────────────────────
        long nowMs = System.currentTimeMillis();
        if (lastSpeedCheckTimeMs > 0 && nowMs > lastSpeedCheckTimeMs) {
            long bytesDelta = totalBytesDownloaded - lastTotalBytesDownloaded;
            long timeDeltaMs = nowMs - lastSpeedCheckTimeMs;
            if (bytesDelta >= 0 && timeDeltaMs > 0) {
                double bytesPerSec = bytesDelta * 1000.0 / timeDeltaMs;
                lastSpeedLabel = formatSpeed(bytesPerSec);
            }
        }
        lastTotalBytesDownloaded = totalBytesDownloaded;
        lastSpeedCheckTimeMs = nowMs;

        String trackTitle = null;
        if (activeDownload != null) {
            trackTitle = DownloaderManager.getDownloadNotificationMessage(activeDownload.request.id);
        }
        if (trackTitle == null) {
            for (Download d : downloads) {
                if (d.state == Download.STATE_QUEUED || d.state == Download.STATE_RESTARTING) {
                    trackTitle = DownloaderManager.getDownloadNotificationMessage(d.request.id);
                    if (trackTitle != null) break;
                }
            }
        }

        boolean allStopped = activeCount > 0;
        for (Download download : downloads) {
            if (download.state == Download.STATE_QUEUED
                    || download.state == Download.STATE_DOWNLOADING
                    || download.state == Download.STATE_RESTARTING) {
                allStopped = false;
                break;
            }
        }
        isPaused = allStopped;

        if (activeCount == 0 && batchTotal > 0) {
            if (!isCancelling) {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                NotificationCompat.Builder doneBuilder = new NotificationCompat.Builder(
                        this, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Download Complete")
                        .setContentText(batchTotal + " tracks downloaded successfully")
                        .setAutoCancel(true);
                manager.notify(3, doneBuilder.build());
            }

            batchTotal = 0;
            lastTotalBytesDownloaded = 0L;
            lastSpeedCheckTimeMs = 0L;
            lastSpeedLabel = "";
            isCancelling = false;
            completedDownloadIds.clear();
            
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(PAUSED_NOTIFICATION_ID);
        }

        String notifTitle;
        String speedSuffix = (!lastSpeedLabel.isEmpty() && !isPaused)
                ? "  •  " + lastSpeedLabel : "";

        if (batchTotal > 0) {
            if (isPaused) {
                notifTitle = "Downloads Paused — " + completed + " of " + batchTotal;
            } else {
                notifTitle = "Downloading " + (completed + 1) + " of " + batchTotal + speedSuffix;
            }
        } else {
            notifTitle = "Downloading" + speedSuffix;
        }

        String contentText = trackTitle;

        NotificationCompat.Action pauseOrResumeAction = isPaused
                ? buildAction(R.drawable.ic_play,
                        getString(R.string.notification_action_resume),
                        Constants.ACTION_RESUME_DOWNLOADS, RC_RESUME)
                : buildAction(R.drawable.ic_pause,
                        getString(R.string.notification_action_pause),
                        Constants.ACTION_PAUSE_DOWNLOADS, RC_PAUSE);

        NotificationCompat.Action cancelAction = buildAction(
                R.drawable.ic_close,
                getString(R.string.notification_action_cancel),
                Constants.ACTION_CANCEL_DOWNLOADS, RC_CANCEL);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(notifTitle)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(pauseOrResumeAction)
                .addAction(cancelAction);

        if (contentText != null && !contentText.isEmpty()) {
            builder.setContentText(contentText);
        }

        if (activeCount > 0 && !isPaused) {
            builder.setProgress(0, 0, true);
        }

        Notification finalNotification = builder.build();

        return finalNotification;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Build a notification action that broadcasts to {@link DownloadControlReceiver}.
     */
    private NotificationCompat.Action buildAction(int iconRes, String label, String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setClass(this, DownloadControlReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(iconRes, label, pendingIntent);
    }

    /**
     * Format bytes/sec as "X.X MB/s" or "X KB/s".
     */
    private static String formatSpeed(double bytesPerSec) {
        if (bytesPerSec >= 1_000_000) {
            return String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_000_000);
        } else if (bytesPerSec >= 1_000) {
            return String.format(Locale.US, "%d KB/s", (int) (bytesPerSec / 1_000));
        } else {
            return String.format(Locale.US, "%d B/s", (int) bytesPerSec);
        }
    }

    /**
     * Build and post a durable paused notification that persists even when the
     * service stops being a foreground service. Called from DownloadControlReceiver
     * when the user taps "Pause".
     */
    public static void postPausedNotification(Context context) {
        DownloadManager downloadManager = DownloadUtil.getDownloadManager(context);
        DownloadIndex downloadIndex = downloadManager.getDownloadIndex();
        List<Download> downloads = new ArrayList<>();
        try (DownloadCursor cursor = downloadIndex.getDownloads()) {
            while (cursor.moveToNext()) {
                downloads.add(cursor.getDownload());
            }
        } catch (IOException e) {
            return;
        }

        // Calculate the same state as getForegroundNotification
        int activeCount = 0;
        long totalBytesDownloaded = 0L;
        Download activeDownload = null;
        for (Download download : downloads) {
            switch (download.state) {
                case Download.STATE_QUEUED:
                case Download.STATE_RESTARTING:
                case Download.STATE_STOPPED:
                    activeCount++;
                    totalBytesDownloaded += download.getBytesDownloaded();
                    break;
                case Download.STATE_DOWNLOADING:
                    activeCount++;
                    totalBytesDownloaded += download.getBytesDownloaded();
                    if (activeDownload == null) {
                        activeDownload = download;
                    }
                    break;
                default:
                    break;
            }
        }

        if (activeCount == 0) {
            return;
        }

        int completed = batchTotal - activeCount;
        if (completed < 0) completed = 0;

        // Determine track title
        String trackTitle = null;
        if (activeDownload != null) {
            trackTitle = DownloaderManager.getDownloadNotificationMessage(activeDownload.request.id);
        }
        if (trackTitle == null) {
            for (Download d : downloads) {
                if (d.state == Download.STATE_QUEUED || d.state == Download.STATE_RESTARTING) {
                    trackTitle = DownloaderManager.getDownloadNotificationMessage(d.request.id);
                    if (trackTitle != null) break;
                }
            }
        }

        // Build paused notification
        String notifTitle = "Downloads Paused — " + completed + " of " + batchTotal;

        NotificationCompat.Action resumeAction = buildActionStatic(
                context, R.drawable.ic_play,
                context.getString(R.string.notification_action_resume),
                Constants.ACTION_RESUME_DOWNLOADS, RC_RESUME);

        NotificationCompat.Action cancelAction = buildActionStatic(
                context, R.drawable.ic_close,
                context.getString(R.string.notification_action_cancel),
                Constants.ACTION_CANCEL_DOWNLOADS, RC_CANCEL);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(notifTitle)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(resumeAction)
                .addAction(cancelAction);

        if (trackTitle != null && !trackTitle.isEmpty()) {
            builder.setContentText(trackTitle);
        }

        Notification finalNotification = builder.build();
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(2);
        notificationManager.notify(2, finalNotification);

        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID);
        notificationManager.cancel(1);
    }

    /**
     * Static version of buildAction for use in static context.
     */
    private static NotificationCompat.Action buildActionStatic(Context context, int iconRes, String label, String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setClass(context, DownloadControlReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(iconRes, label, pendingIntent);
    }

    // ────────────────────────────────────────────────────────────────────────
    // TerminalStateNotificationHelper
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Listens for terminal download state changes and updates the Room database.
     */
    private static final class TerminalStateNotificationHelper implements DownloadManager.Listener {
        private final Context context;
        @SuppressWarnings("FieldCanBeLocal")
        private final DownloadNotificationHelper notificationHelper;

        public TerminalStateNotificationHelper(Context context,
                DownloadNotificationHelper notificationHelper) {
            this.context = context.getApplicationContext();
            this.notificationHelper = notificationHelper;
        }

        @Override
        public void onDownloadChanged(@NonNull DownloadManager downloadManager,
                Download download, @Nullable Exception finalException) {
            if (download.state == Download.STATE_COMPLETED) {
                completedDownloadIds.add(download.request.id);
                DownloaderManager.updateRequestDownload(download);
                ExternalAudioWriter.exportDownloadById(context, download.request.id);
            }
        }

        @Override
        public void onDownloadRemoved(@NonNull DownloadManager downloadManager, Download download) {
            completedDownloadIds.remove(download.request.id);
            DownloaderManager.removeRequestDownload(download);
        }
    }
}
