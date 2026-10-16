package com.eddyizm.tempus.equalizer

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.util.Preferences

@UnstableApi
class ParametricBackend(private val processor: ParametricEqAudioProcessor): EqualizerBackend {

    override fun attach(audioSessionId: Int, context: Context): Boolean {
        val profile = Preferences.getParametricEqProfile()?.let { ParametricProfile.parse(it) }
        processor.setProfile(profile)
        return profile != null
    }

    override fun release(audioSessionId: Int, context: Context) {
        processor.setProfile(null)
    }

    override fun setEnabled(enabled: Boolean) {}
    override fun getNumberOfBands(): Short { return 0 }
    override fun getBandLevelRange(): ShortArray? { return null }
    override fun getCenterFreq(band: Short): Int? { return null }
    override fun getBandLevel(band: Short): Short? { return null }
    override fun setBandLevel(band: Short, level: Short) {}
}
