package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
class PlaylistWithSongs(
    @SerializedName("entry")
    var entries: List<Child>? = null,
) : Playlist(""), Parcelable {

    // Do NOT redeclare `id` here. Overriding the base property creates a second `id`
    // backing field, and Gson then sees two JSON fields named "id" (this class's and
    // Playlist's) and throws "declares multiple JSON fields named 'id'" at startup.
    // The previous workaround mapped this field to "_id", which dodged the clash but
    // left the id null for the Subsonic getPlaylist response (it sends "id"): opening a
    // playlist by id — e.g. a tempo://asset/playlist/<id> deep link — then re-fetched
    // its songs with a null id, the server replied "missing parameter: 'id'", the page
    // showed a perpetual spinner plus a misleading "Playlist not found" dialog, and the
    // null id later crashed with a String.equals NPE. Inheriting Playlist.id (which maps
    // "id" correctly) fixes all of that; the value still survives Parcelize because the
    // base class is @Parcelize too. See issue #729.

    // The synthetic "all songs" search result is built locally with a fixed id.
    constructor(id: String, entries: List<Child>?) : this(entries) {
        this.id = id
    }
}
