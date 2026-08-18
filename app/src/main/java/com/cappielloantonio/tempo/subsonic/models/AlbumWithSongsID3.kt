package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
class AlbumWithSongsID3(
    @SerializedName("song")
    var songs: List<Child>? = null,
) : AlbumID3(), Parcelable