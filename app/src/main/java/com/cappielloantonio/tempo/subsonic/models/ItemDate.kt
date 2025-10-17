package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Keep
@Parcelize
open class ItemDate(
    var year: Int? = null,
    var month: Int? = null,
    var day: Int? = null,
) : Parcelable {
    fun getFormattedDate(): String? {
        if (year == null && month == null && day == null) return null

        val calendar = Calendar.getInstance()
        val dateFormat = if (month == null && day == null) {
            SimpleDateFormat("yyyy", Locale.getDefault())
        } else if (day == null) {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        } else {
            SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        }

        calendar.set(year ?: 0, month ?: 1, day ?: 1)

        return dateFormat.format(calendar.time)
    }
}