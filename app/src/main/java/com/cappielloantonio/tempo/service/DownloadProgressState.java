package com.cappielloantonio.tempo.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.GuardedBy;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.DownloadUtil;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton that tracks external (Path B / ExternalAudioWriter) download progress
 * and maintains a single consolidated progress notification.
 *
 * Thread-safe: all public methods may be called from any thread.
 */
@UnstableApi
public final class DownloadProgressState {

    // Fixed notification IDs — never conflict with Path A IDs (1, 2) or unavailable (1011)
    public static final int EXTERNAL_PROGRESS_NOTIFICATION_ID = 1012;
    public static final int EXTERNAL_COMPLETE_NOTIFICATION_ID = 1013;

    private static volatile DownloadProgressState instance;

    private final Object lock = new Object();

    @GuardedBy("lock") private int enqueuedCount = 0;
    @GuardedBy("lock") private int completedCount = 0;
    @GuardedBy("lock") private int failedCount = 0;
    @GuardedBy("lock") private int skippedCount = 0;

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
            postProgressNotification(context.getApplicationContext());
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

        String contentText;
        if (inFlight > 0) {
            contentText = "Downloading " + doneCount + " of " + total;
        } else {
            contentText = "Processing…";
        }

        Notification notification = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Downloading")
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
            title.append("Downloads complete");
            detail.append(completedCount).append(completedCount == 1 ? " track saved" : " tracks saved");
        } else if (completedCount > 0) {
            title.append("Downloads complete");
            detail.append(completedCount).append(" saved, ").append(failedCount).append(" failed");
        } else if (failedCount > 0) {
            title.append("Downloads failed");
            detail.append(failedCount).append(failedCount == 1 ? " track failed" : " tracks failed");
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
    }
}
