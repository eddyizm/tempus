package com.eddyizm.tempus.helper


import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.TypedValue

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
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

        setSystemBarsColorAmoledOrAccent(activity, applyAmoled)
    }

    /**
     * Decide whether to use hardcoded black
     * or use accent color for its elevation
     */
    @Suppress("DEPRECATION") // Up to API 35
    private fun setSystemBarsColorAmoledOrAccent(activity: AppCompatActivity, isAmoled: Boolean) {
        if (isAmoled) {
            val color = ContextCompat.getColor(activity, android.R.color.black)
            activity.window.navigationBarColor = color
            activity.window.statusBarColor = color
        } else {
            val color8F = SurfaceColors.getColorForElevation(activity, 8F)
            val color0F = SurfaceColors.getColorForElevation(activity, 0F)
            activity.window.navigationBarColor = color8F
            activity.window.statusBarColor = color0F
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
            val amoledOverlayAttrs = mutableListOf(
                android.R.attr.colorBackground,
                android.R.attr.statusBarColor,
                android.R.attr.navigationBarColor,
                com.google.android.material.R.attr.colorSurface,
                com.google.android.material.R.attr.colorSurfaceVariant,
                com.google.android.material.R.attr.colorSurfaceContainerLowest,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                com.google.android.material.R.attr.colorSurfaceContainer,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                com.google.android.material.R.attr.colorOutline,
                com.google.android.material.R.attr.colorOutlineVariant
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                amoledOverlayAttrs.add(android.R.attr.navigationBarDividerColor)
            }

            val typedArray = activity.obtainStyledAttributes(amoledOverlayAttrs.toIntArray())
            typedArray.recycle()

            activity.theme.applyStyle(R.style.AppTheme_Amoled_SurfacesOnly, true)
            activity.window.setBackgroundDrawable(Color.BLACK.toDrawable())
        } else {
            val typedValue = TypedValue()
            activity.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
            activity.window.setBackgroundDrawable(typedValue.data.toDrawable())
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