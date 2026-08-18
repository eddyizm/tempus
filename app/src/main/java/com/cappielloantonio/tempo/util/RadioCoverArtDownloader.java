package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.signature.ObjectKey;
import com.cappielloantonio.tempo.App;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RadioCoverArtDownloader {
    private static final String TAG = "RadioCoverArtDownloader";
    private static final String COVER_DIR = "radio_covers";
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static File getCoverDir() {
        Context context = App.getContext();
        File dir = new File(context.getFilesDir(), COVER_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getLocalCoverFile(String stationId) {
        return new File(getCoverDir(), stationId);
    }

    // Cache-bust Glide by the cover file's mtime so an edited cover (written to the same path)
    // is reloaded instead of served stale from cache. No-op for non-file uris (server covers).
    public static <T> RequestBuilder<T> applyLocalFileSignature(RequestBuilder<T> request, Uri uri) {
        if (uri != null && "file".equals(uri.getScheme()) && uri.getPath() != null) {
            return request.signature(new ObjectKey(new File(uri.getPath()).lastModified()));
        }
        return request;
    }

    public static void downloadCoverArt(String stationId, String coverArtUrl) {
        if (coverArtUrl == null || coverArtUrl.isEmpty()) return;

        executor.execute(() -> {
            try {
                File targetFile = getLocalCoverFile(stationId);
                String urlString = resolveCoverArtUrl(coverArtUrl);

                if (urlString == null) return;

                if (urlString.contains("/rest/getCoverArt")) {
                    downloadViaGlide(urlString, targetFile);
                } else {
                    downloadDirect(urlString, targetFile);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to download cover art for station " + stationId, e);
            }
        });
    }

    private static String resolveCoverArtUrl(String coverArtUrl) {
        if (coverArtUrl.startsWith("http://") || coverArtUrl.startsWith("https://")) {
            return coverArtUrl;
        }
        return com.cappielloantonio.tempo.glide.CustomGlideRequest.createUrl(
                coverArtUrl, Preferences.getImageSize());
    }

    private static boolean downloadDirect(String urlString, File targetFile) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + responseCode + " downloading cover from " + urlString);
                return false;
            }

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download cover from " + urlString, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean downloadViaGlide(String urlString, File targetFile) {
        try {
            Context context = App.getContext();
            File cachedFile = Glide.with(context)
                    .asFile()
                    .load(urlString)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                    .submit()
                    .get();

            if (cachedFile != null && cachedFile.exists()) {
                try (InputStream in = new java.io.FileInputStream(cachedFile);
                     FileOutputStream out = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download cover via Glide from " + urlString, e);
            return false;
        }
    }

    public static void deleteCoverArt(String stationId) {
        executor.execute(() -> {
            File file = getLocalCoverFile(stationId);
            if (file.exists()) {
                file.delete();
            }
        });
    }
}
