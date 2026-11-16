package com.eddyizm.tempus.provider;

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
import com.eddyizm.tempus.BuildConfig;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.util.RadioCoverArtDownloader;

import java.io.File;
import java.io.FileNotFoundException;

public class AlbumArtContentProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".albumart.provider";
    public static final String ALBUM_ART = "albumArt";

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
        if (context == null) {
            throw new FileNotFoundException("Context is null");
        }
        String albumId = uri.getLastPathSegment();
        if (albumId == null) {
            throw new FileNotFoundException("No album ID in URI: " + uri);
        }

        File file = null;
        if (albumId.startsWith("rl_")) {
            File localFile = RadioCoverArtDownloader.getLocalCoverFile(albumId.substring("rl_".length()));
            if (localFile != null && localFile.exists()) {
                file = localFile;
            }
        } else {
            Uri artworkUri;
            if (albumId.startsWith("ir_")) {
                String encodedUrl = albumId.substring("ir_".length());
                String decodedUrl = new String(Base64.decode(encodedUrl, Base64.URL_SAFE | Base64.NO_WRAP));
                artworkUri = Uri.parse(decodedUrl);
            } else {
                artworkUri = Uri.parse(CustomGlideRequest.createUrl(albumId, Preferences.getImageSize()));
            }

            try {
                var fileRequest = Glide.with(context)
                        .asFile()
                        .load(artworkUri)
                        .diskCacheStrategy(DiskCacheStrategy.DATA);
                if (Preferences.isDataSavingMode()) {
                    fileRequest = fileRequest.onlyRetrieveFromCache(true);
                }
                file = fileRequest.submit().get();
            } catch (Exception e) {
                throw new FileNotFoundException("Failed to load image for " + uri + ": " + e.getMessage());
            }
        }

        if (file == null || !file.exists()) {
            throw new FileNotFoundException("Artwork file not found for: " + uri);
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public boolean onCreate() {
        return true;
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
