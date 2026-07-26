package com.cappielloantonio.tempo.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.MediaItem;

import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.service.DownloadProgressState;
import com.cappielloantonio.tempo.subsonic.models.Child;

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

import androidx.media3.common.util.UnstableApi;

@UnstableApi
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
        downloadToUserDirectory(context, child, null, null);
    }

    public static void downloadToUserDirectory(Context context, Child child, String playlistId, String playlistName) {
        if (context == null || child == null) return;
        Context appContext = context.getApplicationContext();
        MediaItem mediaItem = MappingUtil.mapDownload(child);
        String fallbackName = child.getTitle() != null ? child.getTitle() : child.getId();

        // Register with the progress tracker BEFORE submitting to the executor so the
        // total count is accurate even when many tracks are enqueued in rapid succession.
        DownloadProgressState.getInstance().onEnqueue(appContext);

        EXECUTOR.execute(() -> performDownload(appContext, mediaItem, fallbackName, child, playlistId, playlistName));
    }

    private static void performDownload(Context context, MediaItem mediaItem, String fallbackName, Child child, String playlistId, String playlistName) {
        DownloadProgressState.getInstance().setCurrentTrackTitle(
                child.getTitle() != null ? child.getTitle() : fallbackName);

        String uriString = Preferences.getDownloadDirectoryUri();
        if (uriString == null) {
            notifyUnavailable(context);
            DownloadProgressState.getInstance().onFailed(context);
            return;
        }

        DocumentFile directory = DocumentFile.fromTreeUri(context, Uri.parse(uriString));
        if (directory == null || !directory.canWrite()) {
            DownloadProgressState.getInstance().onFailed(context);
            notifyFolderError(context);
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
            ExternalDownloadMetadataStore.remove(metadataKey);
            DownloadProgressState.getInstance().onFailed(context);
            return;
        }

        String scheme = mediaUri.getScheme() != null ? mediaUri.getScheme().toLowerCase(Locale.ROOT) : "";

        HttpURLConnection connection = null;
        DocumentFile sourceDocument = null;
        File sourceFile = null;
        long remoteLength = -1;
        String mimeType = null;
        DocumentFile targetFile = null;

        try {
            if (scheme.equals("http") || scheme.equals("https")) {
                connection = (HttpURLConnection) new URL(mediaUri.toString()).openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    DownloadProgressState.getInstance().onFailed(context);
                    return;
                }

                mimeType = connection.getContentType();
                remoteLength = connection.getContentLengthLong();
            } else if (scheme.equals("content")) {
                sourceDocument = DocumentFile.fromSingleUri(context, mediaUri);
                mimeType = context.getContentResolver().getType(mediaUri);
                if (sourceDocument != null) {
                    remoteLength = sourceDocument.length();
                }
            } else if (scheme.equals("file")) {
                String path = mediaUri.getPath();
                if (path != null) {
                    sourceFile = new File(path);
                    if (sourceFile.exists()) {
                        remoteLength = sourceFile.length();
                    }
                }
                String ext = MimeTypeMap.getFileExtensionFromUrl(mediaUri.toString());
                if (ext != null && !ext.isEmpty()) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                }
            } else {
                ExternalDownloadMetadataStore.remove(metadataKey);
                DownloadProgressState.getInstance().onFailed(context);
                return;
            }

            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }

            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if ((extension == null || extension.isEmpty()) && sourceDocument != null && sourceDocument.getName() != null) {
                String name = sourceDocument.getName();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot < name.length() - 1) {
                    extension = name.substring(dot + 1);
                }
            }
            if ((extension == null || extension.isEmpty()) && sourceFile != null) {
                String name = sourceFile.getName();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && dot < name.length() - 1) {
                    extension = name.substring(dot + 1);
                }
            }
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
                    recordDownload(child, existingFile.getUri(), playlistId, playlistName);
                    ExternalAudioReader.refreshCacheAsync();
                    // Already exists — count as a quiet skip so progress resolves correctly
                    DownloadProgressState.getInstance().onSkipped(context);
                    return;
                } else {
                    existingFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                }
            }

            targetFile = directory.createFile(mimeType, fileName);
            if (targetFile == null) {
                DownloadProgressState.getInstance().onFailed(context);
                return;
            }

            Uri targetUri = targetFile.getUri();
            try (InputStream in = openInputStream(context, mediaUri, scheme, connection, sourceFile);
                 OutputStream out = context.getContentResolver().openOutputStream(targetUri)) {
                if (out == null) {
                    targetFile.delete();
                    DownloadProgressState.getInstance().onFailed(context);
                    return;
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                long total = 0;
                long lastProgressMs = 0;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    total += len;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastProgressMs > 500) {
                        DownloadProgressState.getInstance().reportBytesProgress(context, total);
                        lastProgressMs = now;
                    }
                }
                out.flush();

                if (total <= 0) {
                    targetFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    DownloadProgressState.getInstance().onFailed(context);
                    return;
                }

                if (remoteLength > 0 && total != remoteLength) {
                    targetFile.delete();
                    ExternalDownloadMetadataStore.remove(metadataKey);
                    DownloadProgressState.getInstance().onFailed(context);
                    return;
                }

                ExternalDownloadMetadataStore.recordSize(metadataKey, total);
                recordDownload(child, targetUri, playlistId, playlistName);
                DownloadProgressState.getInstance().onSuccess(context);
                ExternalAudioReader.refreshCacheAsync();
            }
        } catch (Exception e) {
            if (targetFile != null) {
                targetFile.delete();
            }
            ExternalDownloadMetadataStore.remove(metadataKey);
            DownloadProgressState.getInstance().onFailed(context);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Shown only when the user hasn't set a download folder at all — this is a one-off
     * actionable notification that remains separate from the progress flow because the
     * user needs to take action in Settings before anything else can proceed.
     */
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

        manager.notify(DownloadProgressState.EXTERNAL_PROGRESS_NOTIFICATION_ID - 1, builder.build());
    }

    /**
     * Shown only when the download folder exists but is not writable — separate from the
     * progress flow since it indicates a setup problem the user must resolve.
     */
    private static void notifyFolderError(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DownloadUtil.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download folder error")
                .setContentText("Cannot write to the selected folder")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setAutoCancel(true);
        manager.notify(DownloadProgressState.EXTERNAL_PROGRESS_NOTIFICATION_ID - 1, builder.build());
    }

    private static void recordDownload(Child child, Uri fileUri, String playlistId, String playlistName) {
        if (child == null) return;

        Download download = new Download(child);
        download.setDownloadState(1);
        download.setPlaylistId(playlistId);
        download.setPlaylistName(playlistName);

        if (fileUri != null) {
            download.setDownloadUri(fileUri.toString());
        }

        new DownloadRepository().insert(download);
    }   

    private static InputStream openInputStream(Context context,
                                               Uri mediaUri,
                                               String scheme,
                                               HttpURLConnection connection,
                                               File sourceFile) throws IOException {
        switch (scheme) {
            case "http":
            case "https":
                if (connection == null) {
                    throw new IOException("Connection not initialized");
                }
                return connection.getInputStream();
            case "content":
                InputStream contentStream = context.getContentResolver().openInputStream(mediaUri);
                if (contentStream == null) {
                    throw new IOException("Cannot open content stream");
                }
                return contentStream;
            case "file":
                if (sourceFile == null || !sourceFile.exists()) {
                    throw new IOException("Missing source file");
                }
                return new FileInputStream(sourceFile);
            default:
                throw new IOException("Unsupported scheme " + scheme);
        }
    }
}
