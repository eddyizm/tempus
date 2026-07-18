package com.cappielloantonio.tempo.broadcast.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadService;

import com.cappielloantonio.tempo.service.DownloaderService;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;

import java.io.IOException;

/**
 * BroadcastReceiver that handles Pause / Resume / Cancel actions
 * from the consolidated download notification.
 *
 * Using a BroadcastReceiver keeps action handling decoupled from the service
 * internals and avoids fighting Media3's DownloadService.onStartCommand() flow.
 *
 * Action constants are defined in Constants.kt:
 *   - ACTION_PAUSE_DOWNLOADS   → DownloadService.sendPauseDownloads()
 *   - ACTION_RESUME_DOWNLOADS  → DownloadService.sendResumeDownloads()
 *   - ACTION_CANCEL_DOWNLOADS  → removes only QUEUED/DOWNLOADING/STOPPED downloads,
 *                                 leaving already-COMPLETED tracks intact in the cache.
 */
@OptIn(markerClass = UnstableApi.class)
public class DownloadControlReceiver extends BroadcastReceiver {

    private static final String TAG = "DownloadControlReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        switch (intent.getAction()) {
            case Constants.ACTION_PAUSE_DOWNLOADS:
                Log.d(TAG, "Pausing downloads");
                DownloadService.sendPauseDownloads(context, DownloaderService.class, false);
                break;

            case Constants.ACTION_RESUME_DOWNLOADS:
                Log.d(TAG, "Resuming downloads");
                DownloadService.sendResumeDownloads(context, DownloaderService.class, false);
                break;

            case Constants.ACTION_CANCEL_DOWNLOADS:
                Log.d(TAG, "Cancelling active/pending downloads");
                DownloaderService.isCancelling = true;
                cancelActiveDownloads(context);
                break;

            default:
                Log.w(TAG, "Unhandled action: " + intent.getAction());
        }
    }

    /**
     * Removes only downloads that are not yet in a terminal state.
     * Downloads already in STATE_COMPLETED are left untouched so the user
     * keeps songs they already downloaded in a batch.
     *
     * States removed: QUEUED, DOWNLOADING, RESTARTING, STOPPED (paused).
     */
    private void cancelActiveDownloads(Context context) {
        DownloadIndex downloadIndex = DownloadUtil.getDownloadManager(context).getDownloadIndex();
        try (DownloadCursor cursor = downloadIndex.getDownloads(
                Download.STATE_QUEUED,
                Download.STATE_DOWNLOADING,
                Download.STATE_RESTARTING,
                Download.STATE_STOPPED)) {

            while (cursor.moveToNext()) {
                Download download = cursor.getDownload();
                DownloadService.sendRemoveDownload(
                        context,
                        DownloaderService.class,
                        download.request.id,
                        false);
                Log.d(TAG, "Cancelled download: " + download.request.id);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to query downloads for cancel", e);
        }
    }
}
