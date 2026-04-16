package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import kotlinx.parcelize.Parcelize

/**
 * Corresponds to the OpenSubsonic `ReplayGain` object returned inside a
 * `Child` response. All fields are optional — servers that don't implement
 * the OpenSubsonic extension will simply omit the whole object, and
 * individual tags may be missing for tracks that were never scanned.
 *
 * This object is stored as embedded columns (prefix `rg_`) on every Room
 * entity that embeds a `Child`. Null fields become null columns.
 *
 * See: https://opensubsonic.netlify.app/docs/responses/replaygain/
 */
@Keep
@Parcelize
data class ReplayGainInfo(
    @ColumnInfo(name = "track_gain")
    var trackGain: Float? = null,
    @ColumnInfo(name = "album_gain")
    var albumGain: Float? = null,
    @ColumnInfo(name = "track_peak")
    var trackPeak: Float? = null,
    @ColumnInfo(name = "album_peak")
    var albumPeak: Float? = null,
    @ColumnInfo(name = "base_gain")
    var baseGain: Float? = null,
    @ColumnInfo(name = "fallback_gain")
    var fallbackGain: Float? = null,
) : Parcelable {
    /** True if any of the fields carries a meaningful value. */
    fun hasAnyValue(): Boolean =
        trackGain != null || albumGain != null ||
                trackPeak != null || albumPeak != null ||
                baseGain != null || fallbackGain != null
}
