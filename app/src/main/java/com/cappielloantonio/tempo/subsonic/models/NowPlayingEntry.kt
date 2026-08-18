package com.cappielloantonio.tempo.subsonic.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
class NowPlayingEntry(
    @SerializedName("_id")
    override val id: String,
    var username: String? = null,
    var minutesAgo: Int = 0,
    var playerId: Int = 0,
    var playerName: String? = null,
) : Child(id)