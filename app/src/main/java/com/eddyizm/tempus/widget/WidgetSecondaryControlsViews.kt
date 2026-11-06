package com.eddyizm.tempus.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.eddyizm.tempus.R

object WidgetSecondaryControlsViews {

    @JvmStatic
    fun apply(ctx: Context, rv: RemoteViews, shuffleEnabled: Boolean, repeatMode: Int) {
        rv.setImageViewResource(R.id.btn_shuffle, R.drawable.ic_shuffle)
        rv.setImageViewResource(
            R.id.btn_repeat,
            if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        setTint(ctx, rv, R.id.btn_shuffle, shuffleEnabled)
        setTint(ctx, rv, R.id.btn_repeat, repeatMode != Player.REPEAT_MODE_OFF)
    }

    private fun setTint(ctx: Context, rv: RemoteViews, viewId: Int, active: Boolean) {
        val color = if (active) R.color.widget_icon_tint_active else R.color.widget_icon_tint
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rv.setColor(viewId, "setColorFilter", color)
        } else {
            rv.setInt(viewId, "setColorFilter", ContextCompat.getColor(ctx, color))
        }
    }
}
