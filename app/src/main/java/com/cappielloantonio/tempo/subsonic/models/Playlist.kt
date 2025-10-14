package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.Date

@Keep
@Parcelize
@Entity(tableName = "playlist")
open class Playlist(
    @PrimaryKey
    @ColumnInfo(name = "id")
    open var id: String,
    @ColumnInfo(name = "name")
    var name: String? = null,
    @ColumnInfo(name = "duration")
    var duration: Long = 0,
    @ColumnInfo(name = "coverArt")
    var coverArtId: String? = null,
) : Parcelable {
    @Ignore
    @IgnoredOnParcel
    var comment: String? = null
    @Ignore
    @IgnoredOnParcel
    var owner: String? = null
    @Ignore
    @IgnoredOnParcel
    @SerializedName("public")
    var isUniversal: Boolean? = null
    @Ignore
    @IgnoredOnParcel
    var songCount: Int = 0
    @Ignore
    @IgnoredOnParcel
    var created: Date? = null
    @Ignore
    @IgnoredOnParcel
    var changed: Date? = null
    @Ignore
    @IgnoredOnParcel
    var allowedUsers: List<String>? = null
    @Ignore
    constructor(
        id: String,
        name: String?,
        comment: String?,
        owner: String?,
        isUniversal: Boolean?,
        songCount: Int,
        duration: Long,
        created: Date?,
        changed: Date?,
        coverArtId: String?,
        allowedUsers: List<String>?,
    ) : this(id, name, duration, coverArtId) {
        this.comment = comment
        this.owner = owner
        this.isUniversal = isUniversal
        this.songCount = songCount
        this.created = created
        this.changed = changed
        this.allowedUsers = allowedUsers
    }
}