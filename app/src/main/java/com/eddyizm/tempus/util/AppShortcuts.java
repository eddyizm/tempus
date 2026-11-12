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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The shortcuts a launcher shows when the app icon is long-pressed: the playlists and albums the
 * user pinned from their menu first, then the playlists played last.
 *
 * "Pinned" here means pinned to this list. It is neither a playlist pinned to the faves
 * ({@code PinnedPlaylist}) nor what Android calls a pinned shortcut, which is one the user put on
 * the home screen.
 *
 * To add a kind of shortcut, write a method that builds its {@link ShortcutInfoCompat} and add it
 * to the list in {@link #build}; the order of that list is the order the launcher shows. Every
 * shortcut needs an id of its own that stays the same between versions, because a shortcut the
 * user put on the home screen is found again by its id.
 *
 * A playlist or album shortcut starts {@link ShortcutActivity}, which settles how it plays (from
 * the settings, or by asking) and passes its link on to MainActivity with that play mode and the
 * time of the tap. Only a recent tap may start playing, see {@link #playMode}.
 */
public final class AppShortcuts {
    private static final String TAG = "AppShortcuts";

    /** Query parameter of a shortcut's link: the time of the tap, set by {@link #stamp}. */
    private static final String QUERY_SHORTCUT = "shortcut";

    /** Query parameter of a shortcut's link: how it plays, set by {@link #stamp}. */
    private static final String QUERY_PLAY_MODE = "play";

    /**
     * How long after the tap a shortcut's link may still start playing. Android hands an activity
     * the intent that started it again when it recreates it, for example after the process was
     * killed in the background, and that must not start the playlist again.
     */
    private static final long PLAY_WINDOW_MS = 10_000;

    /** Launchers show four entries for an app, so that is all the room there is. */
    public static final int MAX_ITEMS = 4;

    /** Extra of a shortcut's intent: the name, for the title of the dialog that asks how to play. */
    public static final String EXTRA_NAME = "com.eddyizm.tempus.SHORTCUT_NAME";

    /** Extra of a shortcut's intent: its id, to report the shortcut as used. */
    public static final String EXTRA_ID = "com.eddyizm.tempus.SHORTCUT_ID";

    /** An adaptive icon is 108dp, of which the launcher shows the middle 72dp. */
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

    public static boolean isPinned(@NonNull String type, @NonNull String id) {
        return indexOf(Preferences.getPinnedShortcuts(), type, id) >= 0;
    }

    /** Pins an item. False when there is no room left for it. */
    public static boolean pin(@NonNull Context context, @NonNull Preferences.ShortcutItem item) {
        List<Preferences.ShortcutItem> pinned = Preferences.getPinnedShortcuts();
        if (indexOf(pinned, item.getType(), item.getId()) >= 0) return true;
        if (pinned.size() >= MAX_ITEMS) return false;
        pinned.add(item);
        Preferences.setPinnedShortcuts(pinned);
        publish(context);
        return true;
    }

    /** Pins an item in the place of the pinned one at {@code index}. */
    public static void replacePinned(@NonNull Context context, int index, @NonNull Preferences.ShortcutItem item) {
        List<Preferences.ShortcutItem> pinned = Preferences.getPinnedShortcuts();
        if (index < 0 || index >= pinned.size()) return;
        removeById(pinned, item.getType(), item.getId());
        pinned.add(Math.min(index, pinned.size()), item);
        while (pinned.size() > MAX_ITEMS) pinned.remove(pinned.size() - 1);
        Preferences.setPinnedShortcuts(pinned);
        publish(context);
    }

    /** Stores the pinned items as edited in the list: the ones left, in the order given. */
    public static void keepPinned(@NonNull Context context, @NonNull List<Preferences.ShortcutItem> keep) {
        Preferences.setPinnedShortcuts(keep);
        publish(context);
    }

    public static void unpin(@NonNull Context context, @NonNull String type, @NonNull String id) {
        List<Preferences.ShortcutItem> pinned = Preferences.getPinnedShortcuts();
        if (removeById(pinned, type, id)) {
            Preferences.setPinnedShortcuts(pinned);
            publish(context);
        }
    }

    /**
     * How the page opened by this link starts playing: one of the SHORTCUTS_PLAY_MODE values in
     * {@link Preferences}. Only a link a shortcut stamped in the last {@link #PLAY_WINDOW_MS}
     * plays; every other link only opens.
     */
    @NonNull
    public static String playMode(@Nullable AssetLinkUtil.AssetLink assetLink) {
        if (assetLink == null) return Preferences.SHORTCUTS_PLAY_MODE_OPEN;
        String stamp = assetLink.uri.getQueryParameter(QUERY_SHORTCUT);
        long tappedAt;
        try {
            tappedAt = stamp != null ? Long.parseLong(stamp) : 0;
        } catch (NumberFormatException e) {
            tappedAt = 0;
        }
        long age = System.currentTimeMillis() - tappedAt;
        String playMode = assetLink.uri.getQueryParameter(QUERY_PLAY_MODE);
        if (age < 0 || age > PLAY_WINDOW_MS || playMode == null) return Preferences.SHORTCUTS_PLAY_MODE_OPEN;
        return playMode;
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

    /** The shortcut's link with how it plays and the time of the tap, which {@link #playMode} checks. */
    @NonNull
    public static Uri stamp(@NonNull Uri uri, @NonNull String playMode) {
        Uri.Builder builder = uri.buildUpon().clearQuery();
        for (String name : uri.getQueryParameterNames()) {
            if (QUERY_SHORTCUT.equals(name) || QUERY_PLAY_MODE.equals(name)) continue;
            builder.appendQueryParameter(name, uri.getQueryParameter(name));
        }
        return builder
                .appendQueryParameter(QUERY_PLAY_MODE, playMode)
                .appendQueryParameter(QUERY_SHORTCUT, String.valueOf(System.currentTimeMillis()))
                .build();
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
        List<Preferences.ShortcutItem> items = new ArrayList<>(Preferences.getPinnedShortcuts());
        if (withRecent) {
            for (Preferences.ShortcutItem recent : Preferences.getRecentShortcuts()) {
                if (items.size() >= MAX_ITEMS) break;
                if (indexOf(items, recent.getType(), recent.getId()) < 0) items.add(recent);
            }
        }
        while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);

        List<ShortcutInfoCompat> shortcuts = new ArrayList<>();
        for (Preferences.ShortcutItem item : items) {
            shortcuts.add(item(context, item, shortcuts.size()));
        }
        return shortcuts;
    }

    @NonNull
    private static ShortcutInfoCompat item(@NonNull Context context, @NonNull Preferences.ShortcutItem item, int rank) {
        Uri uri = AssetLinkUtil.buildUri(item.getType(), item.getId());
        String label = item.getName() != null && !item.getName().isEmpty()
                ? item.getName() : context.getString(R.string.app_name);
        String id = item.getType() + "_" + item.getId();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri, context, ShortcutActivity.class);
        intent.putExtra(EXTRA_NAME, label);
        intent.putExtra(EXTRA_ID, id);
        return new ShortcutInfoCompat.Builder(context, id)
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
     * of shrinking it onto a white disc. The whole cover goes in the middle the launcher shows,
     * and the cover stretched under it fills the margin that only shows while the launcher
     * animates the icon.
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
