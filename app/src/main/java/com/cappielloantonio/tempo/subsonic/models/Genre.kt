package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
class Genre(
    @SerializedName("value")
    var genre: String? = null,
    var songCount: Int = 0,
    var albumCount: Int = 0,
) : Parcelable