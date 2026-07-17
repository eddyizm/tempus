/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cappielloantonio.tempo.navigation

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cappielloantonio.tempo.R

/**
 * Sets up edge-to-edge for the activity.
 *
 * ```
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         setUpEdgeToEdge()
 *         super.onCreate(savedInstanceState)
 *         ...
 *     }
 * ```
 */
fun ComponentActivity.setUpEdgeToEdge() {
    val impl = if (Build.VERSION.SDK_INT >= 29) {
        EdgeToEdgeApi29()
    } else if (Build.VERSION.SDK_INT >= 26) {
        EdgeToEdgeApi26()
    } else if (Build.VERSION.SDK_INT >= 23) {
        EdgeToEdgeApi23()
    } else if (Build.VERSION.SDK_INT >= 21) {
        EdgeToEdgeApi21()
    } else {
        EdgeToEdgeBase()
    }
    impl.setUp(window, findViewById(android.R.id.content), theme)
}

private fun isDarkMode(resources: Resources): Boolean {
    return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

private interface EdgeToEdgeImpl {
    fun setUp(window: Window, view: View, theme: Resources.Theme)
}

@RequiresApi(29)
private class EdgeToEdgeApi29 : EdgeToEdgeImpl {

    override fun setUp(window: Window, view: View, theme: Resources.Theme) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val resources = view.resources
        val transparent = ResourcesCompat.getColor(resources, android.R.color.transparent, theme)
        val isDarkMode = isDarkMode(resources)
        window.statusBarColor = transparent
        window.navigationBarColor = transparent
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !isDarkMode
        controller.isAppearanceLightNavigationBars = !isDarkMode
    }
}

@RequiresApi(26)
private class EdgeToEdgeApi26 : EdgeToEdgeImpl {

    override fun setUp(window: Window, view: View, theme: Resources.Theme) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val resources = view.resources
        val transparent = ResourcesCompat.getColor(resources, android.R.color.transparent, theme)
        // R.color.navigation_bar_scrim_light is #63FFFFFF for example.
        val scrim = ResourcesCompat.getColor(resources, R.color.navigation_bar_scrim_light, theme)
        window.statusBarColor = transparent
        window.navigationBarColor = scrim
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }
}

@RequiresApi(23)
private class EdgeToEdgeApi23 : EdgeToEdgeImpl {

    override fun setUp(window: Window, view: View, theme: Resources.Theme) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val resources = view.resources
        val transparent = ResourcesCompat.getColor(resources, android.R.color.transparent, theme)
        window.statusBarColor = transparent
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = true
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    }
}

@RequiresApi(21)
private class EdgeToEdgeApi21 : EdgeToEdgeImpl {

    override fun setUp(window: Window, view: View, theme: Resources.Theme) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    }
}

private class EdgeToEdgeBase : EdgeToEdgeImpl {

    override fun setUp(window: Window, view: View, theme: Resources.Theme) {
    }
}
