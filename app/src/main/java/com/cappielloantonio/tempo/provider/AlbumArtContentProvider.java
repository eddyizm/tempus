package com.cappielloantonio.tempo.provider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.RadioCoverArtDownloader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AlbumArtContentProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".albumart.provider";
    public static final String ALBUM_ART = "albumArt";
    private ExecutorService executor;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, "albumArt/*", 1);
    }

    public static Uri contentUri(String artworkId) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY)
                .appendPath(ALBUM_ART)
                .appendPath(artworkId)
                .build();
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context context = getContext();
        String albumId = uri.getLastPathSegment();
        Uri artworkUri = null;
        File localFile = null;

        if (albumId != null && albumId.startsWith("rl_")) {
            // Local radio cover: stored in our app-private files dir, which other processes
            // (SystemUI media controls, Android Auto) cannot open directly. Reading it here in the
            // provider's own process and piping the bytes makes it accessible cross-process.
            localFile = RadioCoverArtDownloader.getLocalCoverFile(albumId.substring("rl_".length()));
        } else if (albumId != null && albumId.startsWith("ir_")) {
            String encodedUrl = albumId.substring("ir_".length());
            String decodedUrl = new String(Base64.decode(encodedUrl, Base64.URL_SAFE | Base64.NO_WRAP));
            artworkUri = Uri.parse(decodedUrl);
        } else {
            artworkUri = Uri.parse(CustomGlideRequest.createUrl(albumId, Preferences.getImageSize()));
        }

        final File localFileFinal = localFile;
        final Uri artworkUriFinal = artworkUri;

        try {
            // use pipe to communicate between background thread and caller of openFile()
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            ParcelFileDescriptor readSide = pipe[0];
            ParcelFileDescriptor writeSide = pipe[1];

            // perform loading in background thread to avoid blocking UI
            executor.execute(() -> {
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {

                    // local radio cover: read directly from disk; otherwise fetch via Glide
                    File file;
                    if (localFileFinal != null) {
                        file = localFileFinal;
                    } else {
                        var fileRequest = Glide.with(context)
                                .asFile()
                                .load(artworkUriFinal)
                                .diskCacheStrategy(DiskCacheStrategy.DATA);
                        if (Preferences.isDataSavingMode()) {
                            fileRequest = fileRequest.onlyRetrieveFromCache(true);
                        }
                        file = fileRequest.submit().get();
                    }

                    // copy artwork down pipe returned by ContentProvider
                    try (InputStream in = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception e) {
                        writeSide.closeWithError("Failed to load image: " + e.getMessage());
                    }

                } catch (Exception e) {
                    try {
                        writeSide.closeWithError("Failed to load image: " + e.getMessage());
                    } catch (IOException ignored) {}
                }
            });

            return readSide;

        } catch (IOException e) {
            throw new FileNotFoundException("Could not create pipe: " + e.getMessage());
        }
    }

    @Override
    public boolean onCreate() {
        executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );
        return true;
    }

    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] strings, @Nullable String s, @Nullable String[] strings1, @Nullable String s1) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }
}
