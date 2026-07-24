package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.Date

@Keep
@Parcelize
open class ArtistID3(
    var id: String? = null,
    var name: String? = null,
    @SerializedName("coverArt")
    var coverArtId: String? = null,
    var albumCount: Int = 0,
    var starred: Date? = null,
) : Parcelable {

    /**
     * #688: a plain [ArtistID3] copy with only the lightweight fields. For heavier
     * subclasses (e.g. ArtistWithAlbumsID3, which carries album/appearsOn lists) this
     * drops those lists so the object stays small when parceled into a navigation
     * argument / saved state. The artist page re-fetches the full artist anyway.
     */
    fun strippedForNav(): ArtistID3 = ArtistID3(id, name, coverArtId, albumCount, starred)
}