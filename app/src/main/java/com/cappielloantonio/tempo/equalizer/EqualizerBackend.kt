package com.cappielloantonio.tempo.equalizer

interface EqualizerBackend {

    fun attachEqualizerIfPossible(audioSessionId: Int): Boolean

    fun attachToSession(audioSessionId: Int): Boolean

    fun release()

    fun setEnabled(enabled: Boolean)

    fun getNumberOfBands(): Short

    fun getBandLevelRange(): ShortArray?

    fun getCenterFreq(band: Short): Int?

    fun getBandLevel(band: Short): Short?

    fun setBandLevel(band: Short, level: Short)
}