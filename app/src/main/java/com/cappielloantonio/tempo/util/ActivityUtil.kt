package com.cappielloantonio.tempo.util

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.helper.ThemeHelper
import com.google.android.material.color.DynamicColors

object ActivityUtil {

    @JvmStatic
    fun enableThemeSwitch(activity: AppCompatActivity) {
        val theme: String = Preferences.getTheme()
        val darkStyle: String = Preferences.getDarkThemeStyle()
        val isAmoled = ThemeHelper.AMOLED_MODE == darkStyle
        var applyAmoled = false

        if (ThemeHelper.DARK_MODE == theme || ThemeHelper.AMOLED_MODE == theme) {
            if (isAmoled) {
                activity.setTheme(R.style.AppTheme_Amoled)
                applyAmoled = true
            }
        } else if (ThemeHelper.DEFAULT_MODE == theme) {
            val nightModeFlags =
                activity.getResources().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES && isAmoled) {
                activity.setTheme(R.style.AppTheme_Amoled)
                applyAmoled = true
            }
        }

        DynamicColors.applyToActivityIfAvailable(activity)
        if (applyAmoled) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_App_Amoled, true)
        }
    }

}