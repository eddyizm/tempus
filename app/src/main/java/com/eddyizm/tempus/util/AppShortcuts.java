package com.eddyizm.tempus.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.Glide;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.ui.activity.ShortcutActivity;
import com.eddyizm.tempus.ui.login.LoginActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The shortcuts a launcher shows when the app icon is long-pressed: the items the user added
 * from a playlist's or album's menu first, then the playlists played last, then the
 * introduction. Launchers show four entries, so there are {@link #MAX_ITEMS} besides the
 * introduction.
 *
 * A shortcut opens its item through an asset link, which {@link ShortcutActivity} stamps with the
 * time of the tap in {@link #QUERY_SHORTCUT}, and only a tap from the last
 * {@link #PLAY_WINDOW_MS} may start playing. Whether it does is read from the settings when the
 * shortcut is used, so changing the setting needs no new shortcuts.
 */
public final class AppShortcuts {
    private static final String TAG = "AppShortcuts";

    private static final String QUERY_SHORTCUT = "shortcut";

    private static final long PLAY_WINDOW_MS = 10_000;

    public static final int MAX_ITEMS = 3;

    private static final String ID_INTRODUCTION = "login_activity_shortcut";
    private static final int ICON_SIZE = 324;
    private static final int ICON_VISIBLE_SIZE = ICON_SIZE * 72 / 108;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AppShortcuts() {
    }

    /** Remembers a playlist the user started, for the recently played shortcuts. */
    public static void playlistStarted(@NonNull Context context, @Nullable Playlist playlist) {
        if (playlist == null || !Preferences.isShortcutsRecentPlaylistsEnabled()) return;
        Preferences.ShortcutItem item = itemOf(playlist);
        List<Preferences.ShortcutItem> recent = Preferences.getRecentShortcuts();
        if (!recent.isEmpty() && recent.get(0).equals(item)) return;
        removeById(recent, item.getType(), item.getId());
        recent.add(0, item);
        while (recent.size() > MAX_ITEMS) recent.remove(recent.size() - 1);
        Preferences.setRecentShortcuts(recent);
        publish(context);
    }

    @NonNull
    public static Preferences.ShortcutItem itemOf(@NonNull Playlist playlist) {
        return new Preferences.ShortcutItem(AssetLinkUtil.TYPE_PLAYLIST, playlist.getId(),
                playlist.getName(), playlist.getCoverArtId());
    }

    @NonNull
    public static Preferences.ShortcutItem itemOf(@NonNull AlbumID3 album) {
        return new Preferences.ShortcutItem(AssetLinkUtil.TYPE_ALBUM, album.getId(),
                album.getName(), album.getCoverArtId());
    }

    public static boolean isFixed(@NonNull String type, @NonNull String id) {
        return indexOf(Preferences.getFixedShortcuts(), type, id) >= 0;
    }

    /** Adds an item the user picked. False when there is no room left for it. */
    public static boolean addFixed(@NonNull Context context, @NonNull Preferences.ShortcutItem item) {
        List<Preferences.ShortcutItem> fixed = Preferences.getFixedShortcuts();
        if (indexOf(fixed, item.getType(), item.getId()) >= 0) return true;
        if (fixed.size() >= MAX_ITEMS) return false;
        fixed.add(item);
        Preferences.setFixedShortcuts(fixed);
        publish(context);
        return true;
    }

    /** Puts an item the user picked in the place of the one at {@code index}. */
    public static void replaceFixed(@NonNull Context context, int index, @NonNull Preferences.ShortcutItem item) {
        List<Preferences.ShortcutItem> fixed = Preferences.getFixedShortcuts();
        if (index < 0 || index >= fixed.size()) return;
        removeById(fixed, item.getType(), item.getId());
        fixed.add(Math.min(index, fixed.size()), item);
        while (fixed.size() > MAX_ITEMS) fixed.remove(fixed.size() - 1);
        Preferences.setFixedShortcuts(fixed);
        publish(context);
    }

    /** Stores the picks as edited in the list: the ones left, in the order given. */
    public static void keepFixed(@NonNull Context context, @NonNull List<Preferences.ShortcutItem> keep) {
        Preferences.setFixedShortcuts(keep);
        publish(context);
    }

    public static void removeFixed(@NonNull Context context, @NonNull String type, @NonNull String id) {
        List<Preferences.ShortcutItem> fixed = Preferences.getFixedShortcuts();
        if (removeById(fixed, type, id)) {
            Preferences.setFixedShortcuts(fixed);
            publish(context);
        }
    }

    /**
     * How the page opened by this link starts playing: one of the SHORTCUTS_PLAY_MODE values in
     * {@link Preferences}. Only a shortcut plays; every other link only opens.
     */
    @NonNull
    public static String playMode(@Nullable AssetLinkUtil.AssetLink assetLink) {
        String stamp = assetLink != null ? assetLink.uri.getQueryParameter(QUERY_SHORTCUT) : null;
        long tappedAt;
        try {
            tappedAt = stamp != null ? Long.parseLong(stamp) : 0;
        } catch (NumberFormatException e) {
            tappedAt = 0;
        }
        long age = System.currentTimeMillis() - tappedAt;
        if (age < 0 || age > PLAY_WINDOW_MS) return Preferences.SHORTCUTS_PLAY_MODE_OPEN;
        return Preferences.getShortcutsPlayMode();
    }

    /**
     * The play mode a shortcut asked for, taken out of a page's arguments so that a rotation or
     * coming back to the page does not start it again. Null when there is none.
     */
    @Nullable
    public static String takePlayMode(@Nullable Bundle args) {
        String playMode = args != null ? args.getString(Constants.AUTO_PLAY) : null;
        if (playMode != null) args.remove(Constants.AUTO_PLAY);
        return playMode;
    }

    /** The shortcut's link with the time of the tap, which {@link #playMode} checks. */
    @NonNull
    public static Uri stamp(@NonNull Uri uri) {
        Uri.Builder builder = uri.buildUpon().clearQuery();
        for (String name : uri.getQueryParameterNames()) {
            if (!QUERY_SHORTCUT.equals(name)) builder.appendQueryParameter(name, uri.getQueryParameter(name));
        }
        return builder.appendQueryParameter(QUERY_SHORTCUT, String.valueOf(System.currentTimeMillis())).build();
    }

    /**
     * Hands the launcher the current list. Cover art is loaded off the main thread, on one thread
     * for every publish, so two publishes never race and the last one wins.
     */
    public static void publish(@NonNull Context context) {
        publish(context, Preferences.isShortcutsRecentPlaylistsEnabled());
    }

    /** As {@link #publish(Context)}, for a settings change that is not saved yet. */
    public static void publish(@NonNull Context context, boolean withRecent) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                ShortcutManagerCompat.setDynamicShortcuts(appContext, build(appContext, withRecent));
            } catch (Exception e) {
                Log.w(TAG, "Could not publish the shortcuts", e);
            }
        });
    }

    @NonNull
    private static List<ShortcutInfoCompat> build(@NonNull Context context, boolean withRecent) {
        List<Preferences.ShortcutItem> items = new ArrayList<>(Preferences.getFixedShortcuts());
        if (withRecent) {
            for (Preferences.ShortcutItem recent : Preferences.getRecentShortcuts()) {
                if (items.size() >= MAX_ITEMS) break;
                if (indexOf(items, recent.getType(), recent.getId()) < 0) items.add(recent);
            }
        }
        while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);

        List<ShortcutInfoCompat> shortcuts = new ArrayList<>();
        for (Preferences.ShortcutItem item : items) {
            shortcuts.add(buildItem(context, item, shortcuts.size()));
        }

        Intent introduction = new Intent(context, LoginActivity.class);
        introduction.setAction(Intent.ACTION_VIEW);
        shortcuts.add(new ShortcutInfoCompat.Builder(context, ID_INTRODUCTION)
                .setShortLabel(context.getString(R.string.la_shortcut_label))
                .setLongLabel(context.getString(R.string.la_shortcut_label))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(introduction)
                .setRank(shortcuts.size())
                .build());
        return shortcuts;
    }

    @NonNull
    private static ShortcutInfoCompat buildItem(@NonNull Context context, @NonNull Preferences.ShortcutItem item, int rank) {
        Uri uri = AssetLinkUtil.buildUri(item.getType(), item.getId()).buildUpon()
                .appendQueryParameter(QUERY_SHORTCUT, "1")
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri, context, ShortcutActivity.class);
        String label = item.getName() != null && !item.getName().isEmpty()
                ? item.getName() : context.getString(R.string.app_name);
        return new ShortcutInfoCompat.Builder(context, item.getType() + "_" + item.getId())
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(loadIcon(context, item))
                .setIntent(intent)
                .setRank(rank)
                .build();
    }

    @NonNull
    private static IconCompat loadIcon(@NonNull Context context, @NonNull Preferences.ShortcutItem item) {
        if (item.getCoverArtId() != null) {
            try {
                Bitmap cover = Glide.with(context)
                        .asBitmap()
                        .load(CustomGlideRequest.createUrl(item.getCoverArtId(), ICON_VISIBLE_SIZE))
                        .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
                        .onlyRetrieveFromCache(Preferences.isDataSavingMode())
                        .centerCrop()
                        .submit(ICON_VISIBLE_SIZE, ICON_VISIBLE_SIZE)
                        .get();
                if (cover != null) return IconCompat.createWithAdaptiveBitmap(toAdaptive(cover));
            } catch (Exception e) {
                Log.d(TAG, "No cover for shortcut " + item.getId(), e);
            }
        }
        return IconCompat.createWithResource(context, R.mipmap.ic_launcher);
    }

    /**
     * The cover laid out for an adaptive icon, so the launcher shapes it like other icons instead
     * of shrinking it onto a white disc. An adaptive icon is 108dp, of which the launcher shows
     * the middle 72dp: the whole cover goes there, and the cover stretched under it fills the
     * margin that only shows while the launcher animates the icon.
     */
    @NonNull
    private static Bitmap toAdaptive(@NonNull Bitmap cover) {
        Bitmap icon = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(cover, null, new Rect(0, 0, ICON_SIZE, ICON_SIZE), paint);
        int inset = (ICON_SIZE - ICON_VISIBLE_SIZE) / 2;
        canvas.drawBitmap(cover, null, new Rect(inset, inset, ICON_SIZE - inset, ICON_SIZE - inset), paint);
        return icon;
    }

    private static int indexOf(@NonNull List<Preferences.ShortcutItem> items, @NonNull String type, @NonNull String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getType().equals(type) && items.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private static boolean removeById(@NonNull List<Preferences.ShortcutItem> items, @NonNull String type, @NonNull String id) {
        int index = indexOf(items, type, id);
        if (index < 0) return false;
        items.remove(index);
        return true;
    }
}
