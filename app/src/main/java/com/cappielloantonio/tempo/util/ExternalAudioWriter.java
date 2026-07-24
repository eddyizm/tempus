package com.cappielloantonio.tempo.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.MediaItem;

import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.activity.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExternalAudioWriter {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private ExternalAudioWriter() {
    }

    public static String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\/:*?\\\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }

    static String normalizeForComparison(String name) {
        String s = sanitizeFileName(name);
        s = Normalizer.normalize(s, Normalizer.Form.NFKD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return s.toLowerCase(Locale.ROOT);
    }

    static DocumentFile findFile(DocumentFile dir, String fileName) {
        String normalized = normalizeForComparison(fileName);
        for (DocumentFile file : dir.listFiles()) {
            if (file.isDirectory()) continue;
            String existing = file.getName();
            if (existing != null && normalizeForComparison(existing).equals(normalized)) {
                return file;
            }
        }
        return null;
    }

    public static void downloadToUserDirectory(Context context, Child child) {
        downloadToUserDirectory(context, child, null, null);
    }

    public static void downloadToUserDirectory(Context context, Child child, String playlistId, String playlistName) {
        if (context == null || child == null) return;
        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            notifyUnavailable(context);
            return;
        }

        MediaItem mediaItem = com.cappielloantonio.tempo.util.MappingUtil.mapDownload(child);
        Download download = new Download(child);
        download.setPlaylistId(playlistId);
        download.setPlaylistName(playlistName);
        
        DownloadUtil.getDownloadTracker(context).download(mediaItem, download);
    }

    public static void exportDownloadById(Context context, String mediaId) {
        String targetDirUri = ExternalDownloadMetadataStore.getExportTarget(mediaId);
        if (targetDirUri == null) {
            return;
        }

        EXECUTOR.execute(() -> {
            DocumentFile targetFile = null;
            try {
                // Remove export target now that we are attempting it
                ExternalDownloadMetadataStore.remove(mediaId);

                // Get the internal download
                androidx.media3.exoplayer.offline.DownloadManager manager = DownloadUtil.getDownloadManager(context);
                androidx.media3.exoplayer.offline.Download exoDownload = manager.getDownloadIndex().getDownload(mediaId);
                if (exoDownload == null || exoDownload.state != androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                    return;
                }

                // Get the database tracking item to know the metadata
                Download dbDownload = new DownloadRepository().getDownload(mediaId);
                if (dbDownload == null) return;

                DocumentFile directory = DocumentFile.fromTreeUri(context, Uri.parse(targetDirUri));
                if (directory == null || !directory.canWrite()) {
                    return;
                }

                String artist = dbDownload.getArtist() != null ? dbDownload.getArtist() : "";
                String title = dbDownload.getTitle() != null ? dbDownload.getTitle() : dbDownload.getId();
                String album = dbDownload.getAlbum() != null ? dbDownload.getAlbum() : "";
                String baseName = artist.isEmpty() ? title : artist + " - " + title;
                if (!album.isEmpty()) baseName += " (" + album + ")";

                String extension = dbDownload.getSuffix();
                if (extension == null || extension.isEmpty()) extension = "mp3";

                String sanitized = sanitizeFileName(baseName);
                if (sanitized.isEmpty()) sanitized = "download";
                String fileName = sanitized + "." + extension;

                targetFile = findFile(directory, fileName);
                if (targetFile != null && targetFile.exists()) {
                    return;
                }

                String mimeType = dbDownload.getContentType();
                if (mimeType == null || mimeType.isEmpty()) mimeType = "audio/mpeg";

                targetFile = directory.createFile(mimeType, fileName);
                if (targetFile == null) return;

                androidx.media3.datasource.DataSource dataSource = DownloadUtil.getUpstreamDataSourceFactory(context).createDataSource();
                androidx.media3.datasource.DataSpec dataSpec = new androidx.media3.datasource.DataSpec(exoDownload.request.uri);
                long length = dataSource.open(dataSpec);

                try (InputStream in = new InputStream() {
                        byte[] single = new byte[1];
                        @Override
                        public int read() throws IOException {
                            int r = read(single);
                            return r == -1 ? -1 : (single[0] & 0xFF);
                        }
                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            return dataSource.read(b, off, len);
                        }
                     };
                     OutputStream out = context.getContentResolver().openOutputStream(targetFile.getUri())) {

                     if (out == null) {
                         targetFile.delete();
                         return;
                     }

                     byte[] buffer = new byte[BUFFER_SIZE];
                     int len;
                     while ((len = in.read(buffer)) != -1) {
                         out.write(buffer, 0, len);
                     }
                     out.flush();
                } finally {
                    dataSource.close();
                }

                ExternalAudioReader.refreshCacheAsync();

                // Update download_uri in database to point to the actual exported file URI
                // This allows "Delete all downloads" to find and delete the external files
                new DownloadRepository().updateDownloadUri(mediaId, targetFile.getUri().toString());

                // Auto-purge the internal Media3 .exo cache now that it's successfully exported to save space
                ExternalDownloadMetadataStore.addPurging(mediaId);
                androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                    context, com.cappielloantonio.tempo.service.DownloaderService.class, mediaId, false);

                } catch (Exception e) {
                    e.printStackTrace();
                    // Remove the partial file so streaming/early failures cannot
                    // leave a truncated download that the reader would surface as
                    // complete and skip re-downloading.
                    if (targetFile != null) targetFile.delete();
                }
        });
    }

    private static void notifyUnavailable(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
        PendingIntent openSettings = PendingIntent.getActivity(context, 0, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("No download folder set")
                .setContentText("Tap to set one in settings")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setContentIntent(openSettings)
                .setAutoCancel(true);

        manager.notify(1011, builder.build());
    }

}
