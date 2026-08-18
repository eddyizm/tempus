package com.cappielloantonio.tempo.equalizer

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class ExternalBackend: EqualizerBackend {

    override fun attach(audioSessionId: Int, context: Context): Boolean {
        if (audioSessionId == 0) return false

        val open = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.applicationContext.packageName)
        }
        context.sendBroadcast(open)
        return true
    }

    override fun release(audioSessionId: Int, context: Context) {
        if (audioSessionId == 0) {
            return
        }

        val close = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.applicationContext.packageName)
        }
        context.sendBroadcast(close)
    }

    override fun setEnabled(enabled: Boolean) {}
    override fun getNumberOfBands(): Short { return 0 }
    override fun getBandLevelRange(): ShortArray? { return null }
    override fun getCenterFreq(band: Short): Int? { return null }
    override fun getBandLevel(band: Short): Short? { return null }
    override fun setBandLevel(band: Short, level: Short) {}
}