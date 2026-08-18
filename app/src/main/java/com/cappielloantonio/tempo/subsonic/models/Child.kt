package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.*

@Keep
@Parcelize
open class Child @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "id")
    open val id: String,
    @ColumnInfo(name = "parent_id")
    @SerializedName("parent")
    var parentId: String? = null,
    @ColumnInfo(name = "is_dir")
    var isDir: Boolean = false,
    @ColumnInfo
    var title: String? = null,
    @ColumnInfo
    var album: String? = null,
    @ColumnInfo
    var artist: String? = null,
    @ColumnInfo
    var track: Int? = null,
    @ColumnInfo
    var year: Int? = null,
    @ColumnInfo
    @SerializedName("genre")
    var genre: String? = null,
    @ColumnInfo(name = "cover_art_id")
    @SerializedName("coverArt")
    var coverArtId: String? = null,
    @ColumnInfo
    var size: Long? = null,
    @ColumnInfo(name = "content_type")
    var contentType: String? = null,
    @ColumnInfo
    var suffix: String? = null,
    @ColumnInfo("transcoding_content_type")
    var transcodedContentType: String? = null,
    @ColumnInfo(name = "transcoded_suffix")
    var transcodedSuffix: String? = null,
    @ColumnInfo
    var duration: Int? = null,
    @ColumnInfo("bitrate")
    @SerializedName("bitRate")
    var bitrate: Int? = null,
    @ColumnInfo("sampling_rate")
    @SerializedName("samplingRate")
    var samplingRate: Int? = null,
    @ColumnInfo("bit_depth")
    @SerializedName("bitDepth")
    var bitDepth: Int? = null,
    @ColumnInfo
    var path: String? = null,
    @ColumnInfo(name = "is_video")
    @SerializedName("isVideo")
    var isVideo: Boolean = false,
    @ColumnInfo(name = "user_rating")
    var userRating: Int? = null,
    @ColumnInfo(name = "average_rating")
    var averageRating: Double? = null,
    @ColumnInfo(name = "play_count")
    var playCount: Long? = null,
    @ColumnInfo(name = "disc_number")
    var discNumber: Int? = null,
    @ColumnInfo
    var created: Date? = null,
    @ColumnInfo
    var starred: Date? = null,
    @ColumnInfo(name = "album_id")
    var albumId: String? = null,
    @ColumnInfo(name = "artist_id")
    var artistId: String? = null,
    @ColumnInfo
    var type: String? = null,
    @ColumnInfo(name = "bookmark_position")
    var bookmarkPosition: Long? = null,
    @ColumnInfo(name = "original_width")
    var originalWidth: Int? = null,
    @ColumnInfo(name = "original_height")
    var originalHeight: Int? = null,
    /**
     * OpenSubsonic ReplayGain data returned as part of the Child response.
     * Stored as embedded columns prefixed `rg_` in every table that persists
     * a Child. May be null for servers that don't implement the extension.
     * See ReplayGainInfo for the exact schema.
     */
    @Embedded(prefix = "rg_")
    @SerializedName("replayGain")
    var replayGain: ReplayGainInfo? = null
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Child) return false

        if (id != other.id) return false
        if (parentId != other.parentId) return false
        if (isDir != other.isDir) return false
        if (title != other.title) return false
        if (album != other.album) return false
        if (artist != other.artist) return false
        if (track != other.track) return false
        if (year != other.year) return false
        if (genre != other.genre) return false
        if (coverArtId != other.coverArtId) return false
        if (size != other.size) return false
        if (contentType != other.contentType) return false
        if (suffix != other.suffix) return false
        if (transcodedContentType != other.transcodedContentType) return false
        if (transcodedSuffix != other.transcodedSuffix) return false
        if (duration != other.duration) return false
        if (bitrate != other.bitrate) return false
        if (samplingRate != other.samplingRate) return false
        if (bitDepth != other.bitDepth) return false
        if (path != other.path) return false
        if (isVideo != other.isVideo) return false
        if (userRating != other.userRating) return false
        if (averageRating != other.averageRating) return false
        if (playCount != other.playCount) return false
        if (discNumber != other.discNumber) return false
        if (created != other.created) return false
        if (starred != other.starred) return false
        if (albumId != other.albumId) return false
        if (artistId != other.artistId) return false
        if (type != other.type) return false
        if (bookmarkPosition != other.bookmarkPosition) return false
        if (originalWidth != other.originalWidth) return false
        if (originalHeight != other.originalHeight) return false
        if (replayGain != other.replayGain) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (parentId?.hashCode() ?: 0)
        result = 31 * result + isDir.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (track ?: 0)
        result = 31 * result + (year ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (coverArtId?.hashCode() ?: 0)
        result = 31 * result + (size?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (suffix?.hashCode() ?: 0)
        result = 31 * result + (transcodedContentType?.hashCode() ?: 0)
        result = 31 * result + (transcodedSuffix?.hashCode() ?: 0)
        result = 31 * result + (duration ?: 0)
        result = 31 * result + (bitrate ?: 0)
        result = 31 * result + (samplingRate ?: 0)
        result = 31 * result + (bitDepth ?: 0)
        result = 31 * result + (path?.hashCode() ?: 0)
        result = 31 * result + isVideo.hashCode()
        result = 31 * result + (userRating ?: 0)
        result = 31 * result + (averageRating?.hashCode() ?: 0)
        result = 31 * result + (playCount?.hashCode() ?: 0)
        result = 31 * result + (discNumber ?: 0)
        result = 31 * result + (created?.hashCode() ?: 0)
        result = 31 * result + (starred?.hashCode() ?: 0)
        result = 31 * result + (albumId?.hashCode() ?: 0)
        result = 31 * result + (artistId?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (bookmarkPosition?.hashCode() ?: 0)
        result = 31 * result + (originalWidth ?: 0)
        result = 31 * result + (originalHeight ?: 0)
        result = 31 * result + (replayGain?.hashCode() ?: 0)
        return result
    }
}