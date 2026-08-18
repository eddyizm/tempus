package com.cappielloantonio.tempo.model

import androidx.annotation.Keep

@Keep
data class ReplayGain(
    var trackGain: Float = 0f,
    var albumGain: Float = 0f,
    var trackPeak: Float = 0f,
    var albumPeak: Float = 0f,
)