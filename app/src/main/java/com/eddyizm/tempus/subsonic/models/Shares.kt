package com.eddyizm.tempus.subsonic.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class Shares {
    @SerializedName("share")
    var shares: List<Share>? = null
}