package com.cappielloantonio.tempo.util

import android.os.Bundle
import com.cappielloantonio.tempo.subsonic.models.ReplayGainInfo

/**
 * Packs / unpacks [ReplayGainInfo] to the [Bundle] used inside
 * MediaItem.mediaMetadata.extras and MediaItem.requestMetadata.extras.
 *
 * Bundles don't natively represent nullable floats; we use NaN as the
 * sentinel for "missing" so that a never-set field round-trips correctly
 * as null.
 */
object ReplayGainBundleUtil {

    private const val KEY_TRACK_GAIN = "rg_trackGain"
    private const val KEY_ALBUM_GAIN = "rg_albumGain"
    private const val KEY_TRACK_PEAK = "rg_trackPeak"
    private const val KEY_ALBUM_PEAK = "rg_albumPeak"
    private const val KEY_BASE_GAIN = "rg_baseGain"
    private const val KEY_FALLBACK_GAIN = "rg_fallbackGain"
    private const val KEY_PRESENT = "rg_present"

    /** Write the RG fields into the given Bundle. No-op if info is null. */
    @JvmStatic
    fun writeToBundle(bundle: Bundle, info: ReplayGainInfo?) {
        if (info == null || !info.hasAnyValue()) {
            bundle.putBoolean(KEY_PRESENT, false)
            return
        }
        bundle.putBoolean(KEY_PRESENT, true)
        bundle.putFloat(KEY_TRACK_GAIN, info.trackGain ?: Float.NaN)
        bundle.putFloat(KEY_ALBUM_GAIN, info.albumGain ?: Float.NaN)
        bundle.putFloat(KEY_TRACK_PEAK, info.trackPeak ?: Float.NaN)
        bundle.putFloat(KEY_ALBUM_PEAK, info.albumPeak ?: Float.NaN)
        bundle.putFloat(KEY_BASE_GAIN, info.baseGain ?: Float.NaN)
        bundle.putFloat(KEY_FALLBACK_GAIN, info.fallbackGain ?: Float.NaN)
    }

    /** Read an RG struct back from the bundle. Returns null if absent. */
    @JvmStatic
    fun fromBundle(bundle: Bundle?): ReplayGainInfo? {
        if (bundle == null || !bundle.getBoolean(KEY_PRESENT, false)) return null
        return ReplayGainInfo(
            trackGain = bundle.getFloat(KEY_TRACK_GAIN, Float.NaN).toNullable(),
            albumGain = bundle.getFloat(KEY_ALBUM_GAIN, Float.NaN).toNullable(),
            trackPeak = bundle.getFloat(KEY_TRACK_PEAK, Float.NaN).toNullable(),
            albumPeak = bundle.getFloat(KEY_ALBUM_PEAK, Float.NaN).toNullable(),
            baseGain = bundle.getFloat(KEY_BASE_GAIN, Float.NaN).toNullable(),
            fallbackGain = bundle.getFloat(KEY_FALLBACK_GAIN, Float.NaN).toNullable(),
        )
    }

    /** True if the bundle carries usable RG data from the server. */
    @JvmStatic
    fun isPresent(bundle: Bundle?): Boolean =
        bundle != null && bundle.getBoolean(KEY_PRESENT, false)

    private fun Float.toNullable(): Float? = if (this.isNaN()) null else this
}
