package com.eddyizm.tempus.widget

import android.content.Context
import android.widget.RemoteViews
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.MusicUtil

class TimingInfo(
    @JvmField val elapsedText: String?,
    @JvmField val totalText: String?,
    @JvmField val progress: Int
)

object WidgetTimingViews {

    @JvmStatic
    fun createTimingInfo(positionMs: Long, durationMs: Long): TimingInfo {
        val duration = if (durationMs > 0) durationMs else 0L
        var position = maxOf(0L, positionMs)
        if (duration > 0 && position > duration) position = duration
        val elapsed = if (duration > 0 || position > 0) MusicUtil.getReadableDurationString(position, true) else null
        val total = if (duration > 0) MusicUtil.getReadableDurationString(duration, true) else null
        val progress = if (duration > 0) {
            (position * WidgetViewsFactory.PROGRESS_MAX / duration)
                .coerceIn(0L, WidgetViewsFactory.PROGRESS_MAX.toLong()).toInt()
        } else {
            0
        }
        return TimingInfo(elapsed, total, progress)
    }

    @JvmStatic
    fun apply(ctx: Context, rv: RemoteViews, elapsedText: String?, totalText: String?, progress: Int) {
        val elapsed = if (elapsedText.isNullOrEmpty()) ctx.getString(R.string.widget_time_elapsed_placeholder) else elapsedText
        val total = if (totalText.isNullOrEmpty()) ctx.getString(R.string.widget_time_duration_placeholder) else totalText
        rv.setTextViewText(R.id.time_elapsed, elapsed)
        rv.setTextViewText(R.id.time_total, total)
        rv.setProgressBar(
            R.id.progress,
            WidgetViewsFactory.PROGRESS_MAX,
            progress.coerceIn(0, WidgetViewsFactory.PROGRESS_MAX),
            false
        )
    }
}
