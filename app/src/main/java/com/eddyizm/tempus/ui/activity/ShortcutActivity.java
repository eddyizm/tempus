package com.eddyizm.tempus.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.util.AppShortcuts;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Where a playlist or album shortcut lands. It settles how the shortcut plays, from the settings
 * or, when they say to ask, with a dialog shown right over the launcher, and passes the link on
 * to MainActivity with that play mode and the time of the tap (see {@link AppShortcuts#playMode}).
 *
 * It runs in a task of its own, so the dialog shows over whatever was on screen instead of
 * bringing the app to the front first.
 */
public class ShortcutActivity extends AppCompatActivity {
    private static final String[] ASK_MODES = {
            Preferences.SHORTCUTS_PLAY_MODE_OPEN,
            Preferences.SHORTCUTS_PLAY_MODE_PLAY,
            Preferences.SHORTCUTS_PLAY_MODE_SHUFFLE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri link = getIntent() != null ? getIntent().getData() : null;
        if (link == null) {
            finish();
            return;
        }
        String playMode = Preferences.getShortcutsPlayMode();
        if (!Preferences.SHORTCUTS_PLAY_MODE_ASK.equals(playMode)) {
            open(link, playMode);
            return;
        }
        // Recreated with the dialog already answered or dismissed: nothing left to do.
        if (savedInstanceState != null) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getIntent().getStringExtra(AppShortcuts.EXTRA_NAME))
                .setItems(R.array.shortcuts_ask_titles, (dialog, which) -> open(link, ASK_MODES[which]))
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    private void open(@NonNull Uri link, @NonNull String playMode) {
        Intent intent = new Intent(Intent.ACTION_VIEW, AppShortcuts.stamp(link, playMode), this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }
}
