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
import androidx.media3.exoplayer.scheduler.PlatformScheduler;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.media3.exoplayer.scheduler.Scheduler;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;
import com.cappielloantonio.tempo.util.ExternalDownloadMetadataStore;

import java.util.List;
import java.util.Locale;

@UnstableApi
public class DownloaderService extends androidx.media3.exoplayer.offline.DownloadService {

    private static final int JOB_ID = 1;
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int PAUSED_NOTIFICATION_ID = 2;
    private static final int COMPLETION_NOTIFICATION_ID = 3;

    // batchTotal is the high-water mark: set when a batch starts, grows if more
    // tracks are added, never shrinks as tracks complete. Reset only after all
    // active downloads reach a terminal state AND a completion notice is posted.
    private static int batchTotal = 0;

    private static TerminalStateNotificationHelper terminalStateListener = null;

    private long lastTime = 0;
    private long lastBytes = 0;
    private double currentSpeedKbS = 0.0;
    private String currentSpeedFormatted = "";

    public DownloaderService() {
        super(FOREGROUND_NOTIFICATION_ID,
                DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
                DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                R.string.exo_download_notification_channel_name,
                0);
    }

    @NonNull
    @Override
    protected DownloadManager getDownloadManager() {
        DownloadManager downloadManager = DownloadUtil.getDownloadManager(this);
        // Guard against duplicate listener registration — getDownloadManager() is
        // called repeatedly by the Media3 framework on the same singleton instance.
        if (terminalStateListener == null) {
            terminalStateListener = new TerminalStateNotificationHelper(this);
            downloadManager.addListener(terminalStateListener);
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

        Context context = getApplicationContext();

        // Active = not yet in a terminal or removal state.
        // Stopped = paused via user action (STATE_STOPPED) or waiting (STATE_QUEUED).
        int activeCount = 0;
        boolean hasStopped = false;
        for (Download d : downloads) {
            if (d.state == Download.STATE_COMPLETED
                    || d.state == Download.STATE_FAILED
                    || d.state == Download.STATE_REMOVING) {
                continue;
            }
            activeCount++;
            if (d.state == Download.STATE_STOPPED || d.state == Download.STATE_QUEUED) {
                hasStopped = true;
            }
        }

        boolean isPaused = getDownloadManager().getDownloadsPaused();

        if (activeCount == 0 && batchTotal > 0) {
            postCompletionNotification(context, batchTotal);
            batchTotal = 0;
            cancelPausedNotification(context);
        } else if (activeCount > batchTotal) {
            batchTotal = activeCount;
        }

        int completedInBatch = (batchTotal > activeCount) ? (batchTotal - activeCount) : 0;
        int totalInBatch = batchTotal;

        String currentSongTitle = "";
        long totalBytesDownloaded = 0;
        long totalContentLength = 0;
        boolean isDownloading = false;

        for (Download download : downloads) {
            if (download.state == Download.STATE_DOWNLOADING) {
                isDownloading = true;
                if (currentSongTitle.isEmpty()) {
                    currentSongTitle = DownloaderManager.getDownloadNotificationMessage(download.request.id);
                }
            }
            totalBytesDownloaded += download.getBytesDownloaded();
            totalContentLength += download.contentLength;
        }

        long now = System.currentTimeMillis();
        if (lastTime > 0 && now > lastTime && isDownloading) {
            long bytesDiff = totalBytesDownloaded - lastBytes;
            long timeDiffMs = now - lastTime;
            if (bytesDiff >= 0 && timeDiffMs > 0) {
                double speed = (bytesDiff / 1024.0) / (timeDiffMs / 1000.0);
                if (speed >= 0) currentSpeedKbS = speed;
            }
        }
        lastTime = now;
        lastBytes = totalBytesDownloaded;

        if (currentSpeedKbS > 1024) {
            currentSpeedFormatted = String.format(Locale.getDefault(), "%.1f MB/s", currentSpeedKbS / 1024.0);
        } else {
            currentSpeedFormatted = String.format(Locale.getDefault(), "%.1f KB/s", currentSpeedKbS);
        }

        if (isPaused && activeCount > 0) {
            postPausedNotification(context, completedInBatch, totalInBatch);
        } else {
            cancelPausedNotification(context);
        }

        String title;
        String contentText;

        if (notMetRequirements != 0) {
            title = "Downloads Paused";
            contentText = ((notMetRequirements & Requirements.NETWORK_UNMETERED) != 0)
                    ? "Waiting for WiFi connection..."
                    : "Waiting for required conditions...";
        } else if (isPaused) {
            title = "Downloads Paused";
            contentText = String.format(Locale.getDefault(), "%d of %d downloaded", completedInBatch, totalInBatch);
        } else {
            int currentNum = Math.min(completedInBatch + 1, totalInBatch);
            title = (totalInBatch > 0)
                    ? String.format(Locale.getDefault(), "Downloading (%d of %d)", currentNum, totalInBatch)
                    : "Downloading Music";
            if (currentSongTitle != null && !currentSongTitle.isEmpty()) {
                contentText = currentSongTitle;
                if (currentSpeedKbS > 0) contentText += " (" + currentSpeedFormatted + ")";
            } else {
                contentText = "Preparing downloads...";
            }
        }

        PendingIntent contentPendingIntent = buildOpenQueueIntent(context);

        NotificationCompat.Action action;
        if (isPaused) {
            action = buildResumeAction(context);
        } else {
            action = buildPauseAction(context);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_download)
                        .setContentTitle(title)
                        .setContentText(contentText)
                        .setContentIntent(contentPendingIntent)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .addAction(action);

        if (totalContentLength > 0 && !isPaused && notMetRequirements == 0) {
            builder.setProgress(100, (int) (totalBytesDownloaded * 100 / totalContentLength), false);
        } else if (isDownloading && notMetRequirements == 0) {
            builder.setProgress(100, 0, true);
        }

        return builder.build();
    }

    private void postPausedNotification(Context context, int completed, int total) {
        String pauseText = (total > 0)
                ? String.format(Locale.getDefault(), "%d of %d downloaded — tap to open", completed, total)
                : "Tap to open download queue";

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_pause)
                        .setContentTitle("Downloads Paused")
                        .setContentText(pauseText)
                        .setContentIntent(buildOpenQueueIntent(context))
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .addAction(buildResumeAction(context));

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(PAUSED_NOTIFICATION_ID, builder.build());
    }

    private void cancelPausedNotification(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(PAUSED_NOTIFICATION_ID);
    }

    private void postCompletionNotification(Context context, int total) {
        String text = total == 1
                ? "1 track downloaded successfully"
                : String.format(Locale.getDefault(), "%d tracks downloaded successfully", total);

        PendingIntent dismissIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(), // no-op broadcast
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_done)
                        .setContentTitle("Download Complete")
                        .setContentText(text)
                        .setContentIntent(buildOpenQueueIntent(context))
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true)
                        .addAction(new NotificationCompat.Action.Builder(
                                R.drawable.ic_done,
                                "Done",
                                dismissIntent).build());

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(COMPLETION_NOTIFICATION_ID, builder.build());
    }

    private PendingIntent buildOpenQueueIntent(Context context) {
        Intent launchIntent = new Intent(context, com.cappielloantonio.tempo.ui.activity.MainActivity.class);
        launchIntent.setAction(Constants.ACTION_OPEN_DOWNLOAD_QUEUE);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private NotificationCompat.Action buildPauseAction(Context context) {
        Intent pauseIntent = androidx.media3.exoplayer.offline.DownloadService.buildPauseDownloadsIntent(
                context, DownloaderService.class, /* foreground= */ false);
        PendingIntent pi = PendingIntent.getService(
                context, 2, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action.Builder(R.drawable.ic_pause, "Pause", pi).build();
    }

    private NotificationCompat.Action buildResumeAction(Context context) {
        Intent resumeIntent = androidx.media3.exoplayer.offline.DownloadService.buildResumeDownloadsIntent(
                context, DownloaderService.class, /* foreground= */ false);
        PendingIntent pi = PendingIntent.getService(
                context, 1, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action.Builder(R.drawable.ic_check_circle, "Resume", pi).build();
    }

    private static final class TerminalStateNotificationHelper implements DownloadManager.Listener {
        private final Context context;

        public TerminalStateNotificationHelper(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void onDownloadChanged(@NonNull DownloadManager downloadManager,
                                      Download download,
                                      @Nullable Exception finalException) {
            // Only responsible for DB side-effects.
            if (download.state == Download.STATE_COMPLETED) {
                DownloaderManager.updateRequestDownload(download);

                // If this download was requested to be exported to the user directory,
                // trigger the export (performed asynchronously by ExternalAudioWriter).
                try {
                    String exportTarget = ExternalDownloadMetadataStore.getExportTarget(download.request.id);
                    if (exportTarget != null) {
                        ExternalAudioWriter.exportDownloadById(context, download.request.id);
                        ExternalDownloadMetadataStore.removeExportTarget(download.request.id);
                    }
                } catch (Exception e) {
                    // Don't let export failures affect notification handling.
                }
            }
        }

        @Override
        public void onDownloadRemoved(@NonNull DownloadManager downloadManager, Download download) {
            DownloaderManager.removeRequestDownload(download);
        }
    }
}
