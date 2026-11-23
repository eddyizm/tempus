package com.eddyizm.tempus.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.widget.RemoteViews

enum class WidgetRefresh { NONE, FULL, SECONDARY_CONTROLS }

object WidgetPartialUpdater {

    private var locales: LocaleList? = null
    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED

    fun hasWidgets(ctx: Context): Boolean = widgetIds(ctx).isNotEmpty()

    fun updateTiming(ctx: Context, positionMs: Long, durationMs: Long) {
        val timing = WidgetTimingViews.createTimingInfo(positionMs, durationMs)
        update(ctx) { rv ->
            WidgetTimingViews.apply(ctx, rv, timing.elapsedText, timing.totalText, timing.progress)
        }
    }

    fun updateSecondaryControls(ctx: Context, shuffleEnabled: Boolean, repeatMode: Int) {
        update(ctx) { rv -> WidgetSecondaryControlsViews.apply(ctx, rv, shuffleEnabled, repeatMode) }
    }

    fun rememberConfiguration(config: Configuration) {
        locales = config.locales
        nightMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    fun refreshAfterConfigurationChange(ctx: Context, newConfig: Configuration): WidgetRefresh {
        val localeChanged = newConfig.locales != locales
        val nightChanged = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) != nightMode
        rememberConfiguration(newConfig)
        return when {
            localeChanged -> if (hasWidgets(ctx)) WidgetRefresh.FULL else WidgetRefresh.NONE
            nightChanged && Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> WidgetRefresh.SECONDARY_CONTROLS
            else -> WidgetRefresh.NONE
        }
    }

    private inline fun update(ctx: Context, fill: (RemoteViews) -> Unit) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in widgetIds(ctx)) {
            val rv = RemoteViews(ctx.packageName, WidgetUpdateManager.chooseBuild(ctx, id).layoutId)
            fill(rv)
            mgr.partiallyUpdateAppWidget(id, rv)
        }
    }

    private fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, WidgetProvider4x1::class.java))
}
