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

import java.util.List;
import java.util.Locale;

/**
 * Foreground service that drives Media3 DownloadManager.
 *
 * ── Change log ──────────────────────────────────────────────────────────────
 * Step 1.1  Removed per-track notification spam (nextNotificationId++).
 *           TerminalStateNotificationHelper now only updates Room DB.
 *           Added listenerRegistered guard (Bug 2 prevention).
 *
 * Step 1.2  getForegroundNotification shows "N of M" batch progress.
 *           Uses Media3's downloads list as ground truth (Bug 3 prevention).
 *           batchTotal high-water mark avoids countdown regression (Bug 1).
 *
 * Step 1.3  Current song title shown as the notification content text.
 *           Looks for the first actively DOWNLOADING item and resolves its
 *           title via DownloaderManager.getDownloadNotificationMessage().
 *
 * Step 1.4  Download speed (KB/s or MB/s) calculated per notification tick
 *           from the summed bytesDownloaded delta across all active downloads.
 *
 * Step 1.5  Pause / Cancel and Resume / Cancel action buttons.
 *           "Pause" is shown while downloading; tapping it flips to "Resume".
 *           Cancel removes only QUEUED/DOWNLOADING/STOPPED items, keeping
 *           already-COMPLETED downloads intact.
 *           Button taps are handled by DownloadControlReceiver.
 * ────────────────────────────────────────────────────────────────────────────
 */
@UnstableApi
public class DownloaderService extends androidx.media3.exoplayer.offline.DownloadService {

    private static final int JOB_ID = 1;
    private static final int FOREGROUND_NOTIFICATION_ID = 1;

    // ── Batch progress tracking ──────────────────────────────────────────────
    // High-water mark: grows to the max active+queued count seen in a batch.
    // Resets when the batch drains to 0. Never shrinks mid-batch (Bug 1 fix).
    private static volatile int batchTotal = 0;

    // Guard: only register the TerminalStateNotificationHelper once per
    // DownloadManager instance, even though getDownloadManager() may be called
    // many times by the framework (Bug 2 fix).
    private static boolean listenerRegistered = false;

    // ── Speed tracking ───────────────────────────────────────────────────────
    // Accumulated bytes across all active downloads at the last notification tick.
    private static long lastTotalBytesDownloaded = 0L;
    // Wall-clock time of the last notification tick (ms).
    private static long lastSpeedCheckTimeMs = 0L;
    // Last computed speed string ("1.2 MB/s", "340 KB/s", etc.).
    private static String lastSpeedLabel = "";

    // ── Pause state ──────────────────────────────────────────────────────────
    // True while all active downloads are STATE_STOPPED (paused by user).
    // Drives whether we show "Pause" or "Resume" button.
    private static volatile boolean isPaused = false;
    public static volatile boolean isCancelling = false;

    // Pending intent request codes (must be unique per PendingIntent).
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

    // ────────────────────────────────────────────────────────────────────────
    // getForegroundNotification — Steps 1.2 + 1.3 + 1.4 + 1.5
    // ────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    protected Notification getForegroundNotification(
            @NonNull List<Download> downloads,
            @Requirements.RequirementFlags int notMetRequirements) {

        // ── Step 1.2: Batch progress ─────────────────────────────────────────
        int activeCount = 0;
        long totalBytesDownloaded = 0L;
        Download activeDownload = null;   // used for Step 1.3 (track title)

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
                        activeDownload = download; // first actively transferring item
                    }
                    break;
                default:
                    // STATE_COMPLETED / STATE_FAILED: terminal, not counted
                    break;
            }
        }

        // Grow the high-water mark; never shrink during an active batch.
        if (activeCount > batchTotal) {
            batchTotal = activeCount;
        }

        int completed = batchTotal - activeCount;

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

        // ── Step 1.3: Track title ─────────────────────────────────────────────
        // Prefer the actively downloading item's title; fall back to any queued item.
        String trackTitle = null;
        if (activeDownload != null) {
            trackTitle = DownloaderManager.getDownloadNotificationMessage(activeDownload.request.id);
        }
        if (trackTitle == null) {
            // Fall back to the first queued item when nothing is actively transferring yet.
            for (Download d : downloads) {
                if (d.state == Download.STATE_QUEUED || d.state == Download.STATE_RESTARTING) {
                    trackTitle = DownloaderManager.getDownloadNotificationMessage(d.request.id);
                    if (trackTitle != null) break;
                }
            }
        }

        // ── Step 1.5: Detect pause state ─────────────────────────────────────
        // Consider the batch paused if every non-terminal download is STOPPED.
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

        // ── Reset when batch drains ───────────────────────────────────────────
        if (activeCount == 0 && batchTotal > 0) {
            // Step 1.8: One-shot completion notification
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
            
            // Clear paused notification if batch just finished/cancelled
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(2);
        }

        // ── Build notification content ─────────────────────────────────────────
        // Title: "Downloading 3 of 10  •  1.2 MB/s" (or "Downloads Paused  3 of 10")
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

        // Content text = current track title (Step 1.3)
        String contentText = trackTitle;

        // ── Step 1.5: Build action buttons ────────────────────────────────────
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

        // ── Assemble final notification ───────────────────────────────────────
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

        // Show indeterminate progress bar while actively downloading.
        if (activeCount > 0 && !isPaused) {
            builder.setProgress(0, 0, true);
        }

        Notification finalNotification = builder.build();

        // ── Step 1.7: Durable paused notification ─────────────────────────────
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (isPaused && activeCount > 0) {
            notificationManager.notify(2, finalNotification);
        } else if (activeCount > 0) {
            notificationManager.cancel(2);
        }

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

    // ────────────────────────────────────────────────────────────────────────
    // TerminalStateNotificationHelper — Step 1.1
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Listens for terminal download state changes and updates the Room database.
     * Per-track notification posting removed in Step 1.1.
     * notificationHelper retained for Step 1.8 (one-shot completion notification).
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
                DownloaderManager.updateRequestDownload(download);
                ExternalAudioWriter.exportDownloadById(context, download.request.id);
            }
        }

        @Override
        public void onDownloadRemoved(@NonNull DownloadManager downloadManager, Download download) {
            DownloaderManager.removeRequestDownload(download);
        }
    }
}
