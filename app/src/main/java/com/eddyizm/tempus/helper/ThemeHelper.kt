package com.eddyizm.tempus.helper


import android.annotation.SuppressLint
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Build
import android.util.Log

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt

import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.Preferences.getColorAccent
import com.eddyizm.tempus.util.Preferences.isDarkThemeBlack
import com.eddyizm.tempus.util.Preferences.isDynamicColorAccent
import com.eddyizm.tempus.util.Preferences.getTheme

import com.google.android.material.color.DynamicColors.applyToActivityIfAvailable
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.elevation.SurfaceColors

object ThemeHelper {
    private const val TAG = "ThemeHelper"

    const val LIGHT_MODE = "light"
    const val DARK_MODE = "dark"
    const val DEFAULT_MODE = "default"

    /**
     * Apply light|night theme to the app.
     */
    @JvmStatic
    fun applyTheme(themePref: String) {
        when (themePref) {
            LIGHT_MODE -> {
                setDefaultNightMode(MODE_NIGHT_NO)
            }

            DARK_MODE -> {
                setDefaultNightMode(MODE_NIGHT_YES)
            }

            else -> {
                setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    /**
     * Apply default|black color to status bar and navigation bar.
     */
    @JvmStatic
    fun setNavigationBarColor(activity: AppCompatActivity) {
        val theme       = getTheme()
        var applyAmoled = false

        if (DARK_MODE == theme) {
            applyAmoled = isDarkThemeBlack()
        } else if (DEFAULT_MODE == theme) {
            val nightModeFlags: Int =
                activity.getResources().configuration.uiMode and UI_MODE_NIGHT_MASK
            applyAmoled = (nightModeFlags == UI_MODE_NIGHT_YES && isDarkThemeBlack())
        }

        if (applyAmoled) {
            activity.window.setNavigationBarColor(ContextCompat.getColor(activity, android.R.color.black))
            activity.window.setStatusBarColor(ContextCompat.getColor(activity, android.R.color.black))
        } else {
            activity.window.setNavigationBarColor(SurfaceColors.getColorForElevation(activity, 8F))
            activity.window.setStatusBarColor(SurfaceColors.getColorForElevation(activity, 0F))
        }
    }

    /**
     * Allow activities to switch among themes when first built.
     * This includes light|night mode and dynamic colors.
     */
    @JvmStatic
    fun enableThemeSwitch(activity: AppCompatActivity) {
        val theme        = getTheme()
        val nightMode    = activity.resources.configuration.uiMode and UI_MODE_NIGHT_MASK
        val isSystemDark = (theme == DEFAULT_MODE && nightMode == UI_MODE_NIGHT_YES)
        val isAmoled     = isDarkThemeBlack() && (theme == DARK_MODE || isSystemDark)

        val colorAccent = getColorAccent()

        when {
            isDynamicColorAccent() -> {
                    applyToActivityIfAvailable(activity)
            }
            colorAccent.startsWith("HEX:") -> {
                val hexString = colorAccent.removePrefix("HEX:")
                try {
                    applyCustomDynamicTheme(activity, hexString)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, e.toString())
                    applyToActivityIfAvailable(activity)
                }
            }
            else -> Unit
        }

        if (isAmoled) {
            val amoledOverlayAttrs = intArrayOf(
                android.R.attr.colorBackground,
                com.google.android.material.R.attr.colorSurface,
                com.google.android.material.R.attr.colorSurfaceVariant
            )

            activity.obtainStyledAttributes(amoledOverlayAttrs)
            activity.theme.applyStyle(R.style.AppTheme_Amoled_SurfacesOnly, true)

        }
    }

    /**
     * Overrides wallpaper colors by using a custom hex as seed.
     */
    private fun applyCustomDynamicTheme(activity: AppCompatActivity, hexString: String) {
        val colorInt = hexString.toColorInt()
        val options = DynamicColorsOptions.Builder()
            .setContentBasedSource(colorInt)
            .setPrecondition { _, _ -> true }
            .build()

        applyToActivityIfAvailable(activity, options)
    }

    @JvmStatic
    fun themeSignature(): String {
        val accent = if (isDynamicColorAccent()) "DYNAMIC" else getColorAccent()
        return "${getTheme()}|${isDarkThemeBlack()}|$accent"
    }
}