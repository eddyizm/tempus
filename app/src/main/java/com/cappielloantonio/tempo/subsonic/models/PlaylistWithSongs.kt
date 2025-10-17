package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
class PlaylistWithSongs(
    @SerializedName("_id")
    override var id: String,
    @SerializedName("entry")
    var entries: List<Child>? = null,
) : Playlist(id), Parcelable