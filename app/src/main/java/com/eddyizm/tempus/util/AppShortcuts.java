package com.eddyizm.tempus.util;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.ui.login.LoginActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The shortcuts a launcher shows when the app icon is long-pressed.
 *
 * To add one, write a method that builds its {@link ShortcutInfoCompat} and add it to the list in
 * {@link #build}; the order of that list is the order the launcher shows. Every shortcut needs an
 * id of its own that stays the same between versions, because a shortcut the user put on the home
 * screen is found again by its id.
 */
public final class AppShortcuts {
    private static final String TAG = "AppShortcuts";

    /** Kept from before this class existed, so introduction shortcuts on home screens keep working. */
    private static final String ID_INTRODUCTION = "login_activity_shortcut";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AppShortcuts() {
    }

    /**
     * Hands the launcher the current list. It runs on one background thread for every publish, so
     * two publishes never race and the last one wins.
     */
    public static void publish(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            try {
                ShortcutManagerCompat.setDynamicShortcuts(appContext, build(appContext));
            } catch (Exception e) {
                Log.w(TAG, "Could not publish the shortcuts", e);
            }
        });
    }

    @NonNull
    private static List<ShortcutInfoCompat> build(@NonNull Context context) {
        List<ShortcutInfoCompat> shortcuts = new ArrayList<>();
        shortcuts.add(introduction(context, shortcuts.size()));
        return shortcuts;
    }

    @NonNull
    private static ShortcutInfoCompat introduction(@NonNull Context context, int rank) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        return new ShortcutInfoCompat.Builder(context, ID_INTRODUCTION)
                .setShortLabel(context.getString(R.string.la_shortcut_label))
                .setLongLabel(context.getString(R.string.la_shortcut_label))
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .setRank(rank)
                .build();
    }
}
