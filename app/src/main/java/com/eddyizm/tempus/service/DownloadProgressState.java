package com.eddyizm.tempus.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.GuardedBy;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.util.DownloadUtil;

import java.util.Locale;

/**
 * Singleton that tracks external (Path B / ExternalAudioWriter) download progress
 * and maintains a single consolidated progress notification.
 *
 * Thread-safe: all public methods may be called from any thread.
 */
@UnstableApi
public final class DownloadProgressState {

    // Fixed notification IDs — Path A uses (1, 2), ExternalAudioWriter errors use (1009, 1010)
    public static final int EXTERNAL_PROGRESS_NOTIFICATION_ID = 1012;
    public static final int EXTERNAL_COMPLETE_NOTIFICATION_ID = 1013;

    private static volatile DownloadProgressState instance;

    private final Object lock = new Object();

    @GuardedBy("lock") private int enqueuedCount = 0;
    @GuardedBy("lock") private int completedCount = 0;
    @GuardedBy("lock") private int failedCount = 0;
    @GuardedBy("lock") private int skippedCount = 0;

    @GuardedBy("lock") private long batchStartTimeMs = 0;
    @GuardedBy("lock") private long lastBytesTotal = 0;
    @GuardedBy("lock") private long lastSampleMs = 0;
    @GuardedBy("lock") private float currentSpeedBytesPerSec = 0f;

    private volatile String currentTrackTitle = null;

    private DownloadProgressState() {}

    public static DownloadProgressState getInstance() {
        if (instance == null) {
            synchronized (DownloadProgressState.class) {
                if (instance == null) {
                    instance = new DownloadProgressState();
                }
            }
        }
        return instance;
    }

    /**
     * Called once per track at the moment it is enqueued into the executor.
     * This increments the total expected count so the "X of N" is accurate.
     */
    public void onEnqueue(Context context) {
        synchronized (lock) {
            enqueuedCount++;
            if (batchStartTimeMs == 0) {
                batchStartTimeMs = SystemClock.elapsedRealtime();
            }
            postProgressNotification(context.getApplicationContext());
        }
    }

    public void setCurrentTrackTitle(String title) {
        this.currentTrackTitle = title;
        synchronized (lock) {
            lastBytesTotal = 0;
            lastSampleMs = SystemClock.elapsedRealtime();
            currentSpeedBytesPerSec = 0f;
        }
    }

    /**
     * Called when a track finishes downloading successfully.
     */
    public void onSuccess(Context context) {
        synchronized (lock) {
            completedCount++;
            checkBatchDone(context.getApplicationContext());
        }
    }

    /**
     * Called when a track was already present — counts as a quiet skip, not an error.
     */
    public void onSkipped(Context context) {
        synchronized (lock) {
            skippedCount++;
            checkBatchDone(context.getApplicationContext());
        }
    }

    /**
     * Called when a track fails to download.
     */
    public void onFailed(Context context) {
        synchronized (lock) {
            failedCount++;
            checkBatchDone(context.getApplicationContext());
        }
    }

    /**
     * Called periodically during an active download to report byte progress.
     * Updates speed and refreshes the progress notification (throttled internally).
     */
    public void reportBytesProgress(Context context, long bytesDownloadedSoFar) {
        synchronized (lock) {
            long now = SystemClock.elapsedRealtime();
            long elapsed = now - lastSampleMs;
            if (elapsed >= 500) {
                currentSpeedBytesPerSec = (bytesDownloadedSoFar - lastBytesTotal) * 1000f / elapsed;
            }
            lastBytesTotal = bytesDownloadedSoFar;
            lastSampleMs = now;
            postProgressNotification(context.getApplicationContext());
        }
    }

    @GuardedBy("lock")
    private void checkBatchDone(Context context) {
        int doneCount = completedCount + failedCount + skippedCount;
        if (doneCount >= enqueuedCount && enqueuedCount > 0) {
            postFinalNotification(context);
            reset();
        } else {
            postProgressNotification(context);
        }
    }

    @GuardedBy("lock")
    private void postProgressNotification(Context context) {
        int doneCount = completedCount + failedCount + skippedCount;
        int total = enqueuedCount;
        int inFlight = total - doneCount;

        String contentTitle = currentTrackTitle != null
                ? context.getString(R.string.notification_downloading_title, currentTrackTitle)
                : context.getString(R.string.notification_downloading);

        String contentText;
        if (total > 0) {
            contentText = context.getString(R.string.notification_download_progress_format, doneCount, total);
            contentText += currentSpeedBytesPerSec > 0f
                    ? " • " + formatSpeed(currentSpeedBytesPerSec)
                    : " • ? KB/s";
        } else {
            contentText = context.getString(R.string.notification_processing);
        }

        Notification notification = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_download)
                .setProgress(total, doneCount, false)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .build();

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(EXTERNAL_PROGRESS_NOTIFICATION_ID, notification);
    }

    @GuardedBy("lock")
    private void postFinalNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Cancel the progress notification
        nm.cancel(EXTERNAL_PROGRESS_NOTIFICATION_ID);

        // Build final summary
        StringBuilder title = new StringBuilder();
        StringBuilder detail = new StringBuilder();

        if (completedCount > 0 && failedCount == 0) {
            title.append(context.getString(R.string.notification_downloads_complete));
            detail.append(context.getResources().getQuantityString(
                    R.plurals.notification_tracks_saved, completedCount, completedCount));
        } else if (completedCount > 0) {
            title.append(context.getString(R.string.notification_downloads_complete));
            detail.append(context.getString(R.string.notification_download_mixed_saved, completedCount, failedCount));
        } else if (failedCount > 0) {
            title.append(context.getString(R.string.notification_download_failed));
            detail.append(context.getResources().getQuantityString(
                    R.plurals.notification_tracks_failed, failedCount, failedCount));
        } else {
            // Only skipped — silently dismiss the progress notification, no final notification needed
            return;
        }

        Notification notification = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(title.toString())
                .setContentText(detail.toString())
                .setSmallIcon(failedCount > 0 && completedCount == 0
                        ? R.drawable.ic_error
                        : R.drawable.ic_check_circle)
                .setOngoing(false)
                .setAutoCancel(false)   // stays until user swipes it away
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .build();

        nm.notify(EXTERNAL_COMPLETE_NOTIFICATION_ID, notification);
    }

    @GuardedBy("lock")
    private void reset() {
        enqueuedCount = 0;
        completedCount = 0;
        failedCount = 0;
        skippedCount = 0;
        batchStartTimeMs = 0;
        lastBytesTotal = 0;
        lastSampleMs = SystemClock.elapsedRealtime();
        currentSpeedBytesPerSec = 0f;
        currentTrackTitle = null;
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
}
