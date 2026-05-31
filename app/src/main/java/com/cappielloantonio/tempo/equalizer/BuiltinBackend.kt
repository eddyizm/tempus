package com.cappielloantonio.tempo.equalizer

import android.content.Context
import android.media.audiofx.Equalizer
import com.cappielloantonio.tempo.util.Preferences

class BuiltinBackend: EqualizerBackend {
    private var equalizer: Equalizer? = null

    override fun attach(audioSessionId: Int, context: Context): Boolean {
        if (audioSessionId == 0 || audioSessionId == -1) return false
        val attached = attachToSession(audioSessionId, context.applicationContext)
        if (attached) {
            val enabled = Preferences.isEqualizerEnabled()
            setEnabled(enabled)
            val bands = getNumberOfBands()
            val savedLevels = Preferences.getEqualizerBandLevels(bands)
            for (i in 0 until bands) {
                setBandLevel(i.toShort(), savedLevels[i])
            }
        }
        return attached
    }

    private fun attachToSession(audioSessionId: Int, context: Context): Boolean {
        release(audioSessionId, context.applicationContext)
        if (audioSessionId != 0 && audioSessionId != -1) {
            try {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                return true
            } catch (e: Exception) {
                // Some devices may not support Equalizer or audio session may be invalid
                equalizer = null
            }
        }
        return false
    }

    override fun release(audioSessionId: Int, context: Context) {
        release()
    }

    private fun release() {
        equalizer?.release()
        equalizer = null
    }

    override fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }

    override fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 0

    override fun getBandLevelRange(): ShortArray? = equalizer?.bandLevelRange

    override fun getCenterFreq(band: Short): Int? =
        equalizer?.getCenterFreq(band)?.div(1000)

    override fun getBandLevel(band: Short): Short? =
        equalizer?.getBandLevel(band)

    override fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }
}
