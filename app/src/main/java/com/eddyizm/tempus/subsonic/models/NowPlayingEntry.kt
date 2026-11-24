package com.eddyizm.tempus.subsonic.models

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
    // state, positionMs and playbackRate are only sent by servers with the playbackReport extension
    var state: String? = null,
    var positionMs: Long? = null,
    var playbackRate: Float? = null,
) : Child(id) {
    companion object {
        const val STATE_STARTING = "starting"
        const val STATE_PLAYING = "playing"
        const val STATE_PAUSED = "paused"
        const val STATE_STOPPED = "stopped"
    }
}
