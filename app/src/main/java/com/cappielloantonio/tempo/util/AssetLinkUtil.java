package com.cappielloantonio.tempo.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.cappielloantonio.tempo.R;

import java.util.Objects;

import com.google.android.material.color.MaterialColors;

public final class AssetLinkUtil {
    public static final String SCHEME = "tempo";
    public static final String HOST_ASSET = "asset";

    public static final String TYPE_SONG = "song";
    public static final String TYPE_ALBUM = "album";
    public static final String TYPE_ARTIST = "artist";
    public static final String TYPE_PLAYLIST = "playlist";
    public static final String TYPE_GENRE = "genre";
    public static final String TYPE_YEAR = "year";

    private AssetLinkUtil() {
    }

    @Nullable
    public static AssetLink parse(@Nullable Intent intent) {
        if (intent == null) return null;
        return parse(intent.getData());
    }

    @Nullable
    public static AssetLink parse(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }

        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return null;
        }

        String host = uri.getHost();
        if (!HOST_ASSET.equalsIgnoreCase(host)) {
            return null;
        }

        if (uri.getPathSegments().size() < 2) {
            return null;
        }

        String type = uri.getPathSegments().get(0);
        String id = uri.getPathSegments().get(1);
        if (TextUtils.isEmpty(type) || TextUtils.isEmpty(id)) {
            return null;
        }

        if (!isSupportedType(type)) {
            return null;
        }

        return new AssetLink(type, id, uri);
    }

    public static boolean isSupportedType(@Nullable String type) {
        if (type == null) return false;
        switch (type) {
            case TYPE_SONG:
            case TYPE_ALBUM:
            case TYPE_ARTIST:
            case TYPE_PLAYLIST:
            case TYPE_GENRE:
            case TYPE_YEAR:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    public static Uri buildUri(@NonNull String type, @NonNull String id) {
        return new Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST_ASSET)
                .appendPath(type)
                .appendPath(id)
                .build();
    }

    @Nullable
    public static String buildLink(@Nullable String type, @Nullable String id) {
        if (TextUtils.isEmpty(type) || TextUtils.isEmpty(id) || !isSupportedType(type)) {
            return null;
        }
        return buildUri(Objects.requireNonNull(type), Objects.requireNonNull(id)).toString();
    }

    @Nullable
    public static AssetLink buildAssetLink(@Nullable String type, @Nullable String id) {
        String link = buildLink(type, id);
        return parseLinkString(link);
    }

    @Nullable
    public static AssetLink parseLinkString(@Nullable String link) {
        if (TextUtils.isEmpty(link)) {
            return null;
        }
        return parse(Uri.parse(link));
    }

    public static void copyToClipboard(@NonNull Context context, @NonNull AssetLink assetLink) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            return;
        }
        ClipData clipData = ClipData.newPlainText(context.getString(R.string.asset_link_clipboard_label), assetLink.uri.toString());
        clipboardManager.setPrimaryClip(clipData);
    }

    @StringRes
    public static int getLabelRes(@NonNull String type) {
        switch (type) {
            case TYPE_SONG:
                return R.string.asset_link_label_song;
            case TYPE_ALBUM:
                return R.string.asset_link_label_album;
            case TYPE_ARTIST:
                return R.string.asset_link_label_artist;
            case TYPE_PLAYLIST:
                return R.string.asset_link_label_playlist;
            case TYPE_GENRE:
                return R.string.asset_link_label_genre;
            case TYPE_YEAR:
                return R.string.asset_link_label_year;
            default:
                return R.string.asset_link_label_unknown;
        }
    }

    public static void applyLinkAppearance(@NonNull View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (textView.getTag(R.id.tag_link_original_color) == null) {
                textView.setTag(R.id.tag_link_original_color, textView.getCurrentTextColor());
            }
            int accent = MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary,
                    ContextCompat.getColor(view.getContext(), android.R.color.holo_blue_light));
            textView.setTextColor(accent);
        }
    }

    public static void clearLinkAppearance(@NonNull View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Object original = textView.getTag(R.id.tag_link_original_color);
            if (original instanceof Integer) {
                textView.setTextColor((Integer) original);
            } else {
                int defaultColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface,
                        ContextCompat.getColor(view.getContext(), android.R.color.primary_text_light));
                textView.setTextColor(defaultColor);
            }
        }
    }

    public static final class AssetLink {
        public final String type;
        public final String id;
        public final Uri uri;

        AssetLink(@NonNull String type, @NonNull String id, @NonNull Uri uri) {
            this.type = type;
            this.id = id;
            this.uri = uri;
        }
    }
}
