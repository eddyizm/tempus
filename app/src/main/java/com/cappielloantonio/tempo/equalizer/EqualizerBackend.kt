package com.cappielloantonio.tempo.equalizer

import android.content.Context

interface EqualizerBackend {

    fun attach(audioSessionId: Int, context: Context): Boolean

    fun release(audioSessionId: Int, context: Context)

    fun setEnabled(enabled: Boolean)

    fun getNumberOfBands(): Short

    fun getBandLevelRange(): ShortArray?

    fun getCenterFreq(band: Short): Int?

    fun getBandLevel(band: Short): Short?

    fun setBandLevel(band: Short, level: Short)
}