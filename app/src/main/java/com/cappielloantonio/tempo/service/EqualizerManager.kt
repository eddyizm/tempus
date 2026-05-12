package com.cappielloantonio.tempo.service

import android.media.audiofx.Equalizer
import com.cappielloantonio.tempo.util.Preferences

class EqualizerManager {

    private var equalizer: Equalizer? = null

    fun attachEqualizerIfPossible(audioSessionId: Int): Boolean {
        if (audioSessionId == 0 || audioSessionId == -1) return false
        val attached = attachToSession(audioSessionId)
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

    fun attachToSession(audioSessionId: Int): Boolean {
        release()
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

    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }

    fun getNumberOfBands(): Short = equalizer?.numberOfBands ?: 0

    fun getBandLevelRange(): ShortArray? = equalizer?.bandLevelRange

    fun getCenterFreq(band: Short): Int? =
        equalizer?.getCenterFreq(band)?.div(1000)

    fun getBandLevel(band: Short): Short? =
        equalizer?.getBandLevel(band)

    fun setEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}