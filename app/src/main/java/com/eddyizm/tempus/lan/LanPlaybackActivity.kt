package com.eddyizm.tempus.lan

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.eddyizm.tempus.R
import com.eddyizm.tempus.helper.ThemeHelper
import com.eddyizm.tempus.navigation.setUpEdgeToEdge
import com.google.android.material.appbar.MaterialToolbar

/** Hosts remote-player preferences using the same toolbar and rows as Settings. */
class LanPlaybackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.enableThemeSwitch(this)
        setUpEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_settings)

        findViewById<MaterialToolbar>(R.id.settings_toolbar).apply {
            setTitle(R.string.lan_title)
            setNavigationOnClickListener { finish() }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, LanSettingsFragment())
                .commit()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        LanRemoteVolume.dispatch(event) || super.dispatchKeyEvent(event)
}
