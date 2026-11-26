package com.eddyizm.tempus.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatToggleButton
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.helper.ThemeHelper
import com.eddyizm.tempus.ui.login.LoginActivity
import com.eddyizm.tempus.util.Preferences
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongFavoriteContrastTest {
    @Test fun favoriteHeartHasContrastInBothStates() {
        val prefs = App.getInstance().preferences
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for ((mode, amoled) in listOf(
            Configuration.UI_MODE_NIGHT_YES to true,
            Configuration.UI_MODE_NIGHT_YES to false,
            Configuration.UI_MODE_NIGHT_NO to false
        )) {
            val savedTheme = prefs.getString(Preferences.THEME, null)
            val savedDarkThemeBlack = prefs.all[Preferences.DARK_THEME_BLACK] as Boolean?
            val savedAccent = prefs.getString(Preferences.COLOR_ACCENT, null)
            val savedDynamicAccent = prefs.all[Preferences.DYNAMIC_COLOR_ACCENT] as Boolean?
            val savedNightMode = AppCompatDelegate.getDefaultNightMode()
            val theme = if (mode == Configuration.UI_MODE_NIGHT_YES) ThemeHelper.DARK_MODE else ThemeHelper.LIGHT_MODE
            try {
                prefs.edit(commit = true) {
                    putString(Preferences.THEME, theme)
                    putBoolean(Preferences.DARK_THEME_BLACK, amoled)
                    remove(Preferences.COLOR_ACCENT)
                    putBoolean(Preferences.DYNAMIC_COLOR_ACCENT, false)
                }
                instrumentation.runOnMainSync {
                    ThemeHelper.applyTheme(theme)
                }
                ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        assertEquals(mode, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        val fragment = BottomSheetDialogFragment()
                        fragment.showNow(activity.supportFragmentManager, null)
                        val sheet = fragment.layoutInflater.inflate(R.layout.bottom_sheet_song_dialog, null)
                        val toggle = sheet.findViewById<AppCompatToggleButton>(R.id.button_favorite)
                        assertHeartContrast(toggle, mode, amoled)
                    }
                }
            } finally {
                prefs.edit(commit = true) {
                    putString(Preferences.THEME, savedTheme)
                    putString(Preferences.COLOR_ACCENT, savedAccent)
                    if (savedDarkThemeBlack == null) remove(Preferences.DARK_THEME_BLACK)
                    else putBoolean(Preferences.DARK_THEME_BLACK, savedDarkThemeBlack)
                    if (savedDynamicAccent == null) remove(Preferences.DYNAMIC_COLOR_ACCENT)
                    else putBoolean(Preferences.DYNAMIC_COLOR_ACCENT, savedDynamicAccent)
                }
                instrumentation.runOnMainSync {
                    if (savedNightMode != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED) {
                        AppCompatDelegate.setDefaultNightMode(savedNightMode)
                    } else {
                        ThemeHelper.applyTheme(Preferences.getTheme())
                    }
                }
            }
        }
    }

    private fun assertHeartContrast(toggle: AppCompatToggleButton, mode: Int, amoled: Boolean) {
        val surface = MaterialColors.getColor(toggle, com.google.android.material.R.attr.colorSurface)
        val expectedSurface = when {
            amoled -> Color.BLACK
            mode == Configuration.UI_MODE_NIGHT_YES -> toggle.context.getColor(R.color.md_theme_dark_surface)
            else -> toggle.context.getColor(R.color.md_theme_light_surface)
        }
        assertEquals("Unexpected surface for mode=$mode amoled=$amoled", expectedSurface, surface)
        if (amoled) {
            val onSurface = MaterialColors.getColor(toggle, com.google.android.material.R.attr.colorOnSurface)
            assertEquals("AMOLED heart tint", onSurface, toggle.backgroundTintList?.defaultColor)
        }
        for (checked in listOf(false, true)) {
            toggle.isChecked = checked
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            toggle.background.setBounds(0, 0, bitmap.width, bitmap.height)
            toggle.background.draw(Canvas(bitmap))
            val pixels = IntArray(48 * 48)
            bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48)
            val opaque = pixels.filter { Color.alpha(it) == 255 }
            assertTrue("Missing heart for mode=$mode amoled=$amoled checked=$checked", opaque.isNotEmpty())
            assertTrue("Low contrast for mode=$mode amoled=$amoled checked=$checked",
                opaque.all { ColorUtils.calculateContrast(it, surface) >= 3.0 })
            assertEquals(if (checked) 255 else 0, Color.alpha(bitmap.getPixel(24, 24)))
        }
    }
}
