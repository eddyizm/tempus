package com.eddyizm.tempus.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatToggleButton
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eddyizm.tempus.R
import com.eddyizm.tempus.ui.login.LoginActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongFavoriteContrastTest {
    @Test fun favoriteHeartHasContrastInBothStates() {
        for ((mode, amoled) in listOf(
            Configuration.UI_MODE_NIGHT_YES to true,
            Configuration.UI_MODE_NIGHT_YES to false,
            Configuration.UI_MODE_NIGHT_NO to false
        )) {
            ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.delegate.localNightMode = if (mode == Configuration.UI_MODE_NIGHT_YES)
                        AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                }
                scenario.onActivity { activity ->
                    assertEquals(mode, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                    if (amoled) activity.theme.applyStyle(R.style.AppTheme_Amoled_SurfacesOnly, true)
                    val fragment = BottomSheetDialogFragment()
                    fragment.showNow(activity.supportFragmentManager, null)
                    val sheet = fragment.layoutInflater.inflate(R.layout.bottom_sheet_song_dialog, null)
                    val toggle = sheet.findViewById<AppCompatToggleButton>(R.id.button_favorite)
                    assertHeartContrast(toggle, mode, amoled)
                }
            }
        }
    }

    private fun assertHeartContrast(toggle: AppCompatToggleButton, mode: Int, amoled: Boolean) {
        val surface = MaterialColors.getColor(toggle, com.google.android.material.R.attr.colorSurface)
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
            assertTrue("Missing heart for mode=$mode checked=$checked", opaque.isNotEmpty())
            assertTrue("Low contrast for mode=$mode checked=$checked",
                opaque.all { ColorUtils.calculateContrast(it, surface) >= 3.0 })
            assertEquals(if (checked) 255 else 0, Color.alpha(bitmap.getPixel(24, 24)))
        }
    }
}
