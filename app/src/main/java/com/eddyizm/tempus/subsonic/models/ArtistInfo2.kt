package com.eddyizm.tempus.subsonic.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class ArtistInfo2 : ArtistInfoBase() {
    @SerializedName("similarArtist")
    var similarArtists: List<SimilarArtistID3>? = emptyList()
}