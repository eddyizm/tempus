package com.eddyizm.tempus.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.eddyizm.tempus.util.AppShortcuts;

/**
 * Where a launcher shortcut lands. It passes the link on to MainActivity, stamped with the time of
 * the tap. Android keeps the intent that started an activity and hands it to it again when it
 * recreates it, for example after the process was killed in the background, so an unstamped
 * shortcut would start playing again on every such restart.
 */
public class ShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null && getIntent().getData() != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, AppShortcuts.stamp(getIntent().getData()),
                    this, MainActivity.class);
            startActivity(intent);
        }
        finish();
    }
}
