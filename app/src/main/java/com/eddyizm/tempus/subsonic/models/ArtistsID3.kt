package com.eddyizm.tempus.subsonic.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class ArtistsID3 {
    @SerializedName("index")
    var indices: List<IndexID3>? = null
    var ignoredArticles: String? = null
}