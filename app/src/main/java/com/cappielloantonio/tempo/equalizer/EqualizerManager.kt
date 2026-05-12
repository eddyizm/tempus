package com.cappielloantonio.tempo.equalizer

class EqualizerManager(
    private var backend: EqualizerBackend = BuiltinBackend()
) {

    fun setBackend(newBackend: EqualizerBackend) {
        backend.release()
        backend = newBackend
    }

    fun attachEqualizerIfPossible(audioSessionId: Int): Boolean {
        return backend.attachEqualizerIfPossible(audioSessionId)
    }

    fun setBandLevel(band: Short, level: Short) = backend.setBandLevel(band, level)
    fun getNumberOfBands(): Short = backend.getNumberOfBands()
    fun getBandLevelRange(): ShortArray? = backend.getBandLevelRange()
    fun getCenterFreq(band: Short): Int? = backend.getCenterFreq(band)
    fun getBandLevel(band: Short): Short? = backend.getBandLevel(band)
    fun setEnabled(enabled: Boolean) = backend.setEnabled(enabled)
    fun release() = backend.release()
}