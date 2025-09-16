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

import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.activity.MainActivity;

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

    private static String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[\\/:*?\\\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }

    private static String normalizeForComparison(String name) {
        String s = sanitizeFileName(name);
        s = Normalizer.normalize(s, Normalizer.Form.NFKD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return s.toLowerCase(Locale.ROOT);
    }

    private static DocumentFile findFile(DocumentFile dir, String fileName) {
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
        if (context == null || child == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        MediaItem mediaItem = MappingUtil.mapDownload(child);
        String fallbackName = child.getTitle() != null ? child.getTitle() : child.getId();
        EXECUTOR.execute(() -> performDownload(appContext, mediaItem, fallbackName, child));
    }

    private static void performDownload(Context context, MediaItem mediaItem, String fallbackName, Child child) {
        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            notifyUnavailable(context);
            return;
        }

        DocumentFile directory = DocumentFile.fromTreeUri(context, Uri.parse(uriString));
        if (directory == null || !directory.canWrite()) {
            notifyFailure(context, "Cannot write to folder.");
            return;
        }

        String artist = child.getArtist() != null ? child.getArtist() : "";
        String title = child.getTitle() != null ? child.getTitle() : fallbackName;
        String album = child.getAlbum() != null ? child.getAlbum() : "";
        String baseName = artist.isEmpty() ? title : artist + " - " + title;
        if (!album.isEmpty()) baseName += " (" + album + ")";
        if (baseName.isEmpty()) {
            baseName = fallbackName != null ? fallbackName : "download";
        }
        String metadataKey = normalizeForComparison(baseName);

        Uri mediaUri = mediaItem != null && mediaItem.requestMetadata != null
                ? mediaItem.requestMetadata.mediaUri
                : null;
        if (mediaUri == null) {
            notifyFailure(context, "Invalid media URI.");
            ExternalDownloadMetadataStore.remove(metadataKey);
            return;
        }
        String scheme = mediaUri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            notifyFailure(context, "Unsupported media URI.");
            ExternalDownloadMetadataStore.remove(metadataKey);
            return;
        }

        HttpURLConnection connection = null;
        DocumentFile targetFile = null;
        try {
            connection = (HttpURLConnection) new URL(mediaUri.toString()).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                notifyFailure(context, "Server returned " + responseCode);
                ExternalDownloadMetadataStore.remove(metadataKey);
                return;
            }

            String mimeType = connection.getContentType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }

            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension == null || extension.isEmpty()) {
                String suffix = child.getSuffix();
                if (suffix != null && !suffix.isEmpty()) {
                    extension = suffix;
                } else {
                    extension = "bin";
                }
            }

            String sanitized = sanitizeFileName(baseName);
            if (sanitized.isEmpty()) sanitized = sanitizeFileName(fallbackName);
            if (sanitized.isEmpty()) sanitized = "download";
            String fileName = sanitized + "." + extension;

            DocumentFile existingFile = findFile(directory, fileName);
            long remoteLength = connection.getContentLengthLong();
            Long recordedSize = ExternalDownloadMetadataStore.getSize(metadataKey);
            if (existingFile != null && existingFile.exists()) {
                long localLength = existingFile.length();
                boolean matches = false;
                if (remoteLength > 0 && localLength == remoteLength) {
                    matches = true;
                } else if (remoteLength <= 0 && recordedSize != null && localLength == recordedSize) {
                    matches = true;
                }
                if (matches) {
                    ExternalDownloadMetadataStore.recordSize(metadataKey, localLength);
                    ExternalAudioReader.refreshCache();
                    notifyExists(context, fileName);
                    return;
                } else {
                    existingFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                }
            }

            targetFile = directory.createFile(mimeType, fileName);
            if (targetFile == null) {
                notifyFailure(context, "Failed to create file.");
                return;
            }

            Uri targetUri = targetFile.getUri();
            try (InputStream in = connection.getInputStream();
                 OutputStream out = context.getContentResolver().openOutputStream(targetUri)) {
                if (out == null) {
                    notifyFailure(context, "Cannot open output stream.");
                    targetFile.delete();
                    return;
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                long total = 0;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    total += len;
                }
                out.flush();

                if (total <= 0) {
                    targetFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    notifyFailure(context, "Empty download.");
                    return;
                }

                if (remoteLength > 0 && total != remoteLength) {
                    targetFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    notifyFailure(context, "Incomplete download.");
                    return;
                }

                ExternalDownloadMetadataStore.recordSize(metadataKey, total);
                notifySuccess(context, fileName, child, targetUri);
                ExternalAudioReader.refreshCache();
            }
        } catch (Exception e) {
            if (targetFile != null) {
                targetFile.delete();
            }
            ExternalDownloadMetadataStore.remove(metadataKey);
            notifyFailure(context, e.getMessage() != null ? e.getMessage() : "Download failed");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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

    private static void notifyFailure(Context context, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download failed")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private static void notifySuccess(Context context, String name, Child child, Uri fileUri) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download complete")
                .setContentText(name)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true);

        PendingIntent playIntent = buildPlayIntent(context, child, fileUri);
        if (playIntent != null) {
            builder.setContentIntent(playIntent);
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private static void notifyExists(Context context, String name) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Already downloaded")
                .setContentText(name)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private static PendingIntent buildPlayIntent(Context context, Child child, Uri fileUri) {
        if (fileUri == null) return null;
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Constants.ACTION_PLAY_EXTERNAL_DOWNLOAD)
                .putExtra(Constants.EXTRA_DOWNLOAD_URI, fileUri.toString())
                .putExtra(Constants.EXTRA_DOWNLOAD_MEDIA_ID, child.getId())
                .putExtra(Constants.EXTRA_DOWNLOAD_TITLE, child.getTitle())
                .putExtra(Constants.EXTRA_DOWNLOAD_ARTIST, child.getArtist())
                .putExtra(Constants.EXTRA_DOWNLOAD_ALBUM, child.getAlbum())
                .putExtra(Constants.EXTRA_DOWNLOAD_DURATION, child.getDuration() != null ? child.getDuration() : 0)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode;
        if (child.getId() != null) {
            requestCode = Math.abs(child.getId().hashCode());
        } else {
            requestCode = Math.abs(fileUri.toString().hashCode());
        }

        return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
