package com.cappielloantonio.tempo.lyrics.lrclib

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class LrcLibResponse(
    @SerializedName(value = "syncedLyrics", alternate = ["synced_lyrics"])
    val syncedLyrics: String? = null,
    @SerializedName(value = "plainLyrics", alternate = ["plain_lyrics"])
    val plainLyrics: String? = null,
    @SerializedName(value = "language", alternate = ["lang"])
    val language: String? = null
)

