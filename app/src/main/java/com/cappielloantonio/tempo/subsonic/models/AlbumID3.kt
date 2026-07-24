package com.cappielloantonio.tempo.subsonic.models

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.Date

@Keep
@Parcelize
open class AlbumID3(
    var id: String? = null,
    var name: String? = null,
    var artist: String? = null,
    var artistId: String? = null,
    @SerializedName("coverArt")
    var coverArtId: String? = null,
    var songCount: Int? = 0,
    var duration: Int? = 0,
    var playCount: Long? = 0,
    var created: Date? = null,
    var starred: Date? = null,
    var year: Int = 0,
    var genre: String? = null,
    var played: Date? = Date(0),
    var userRating: Int? = 0,
    var recordLabels: List<RecordLabel>? = null,
    var musicBrainzId: String? = null,
    var genres: List<ItemGenre>? = null,
    var artists: List<ArtistID3>? = null,
    var displayArtist: String? = null,
    var releaseTypes: List<String>? = null,
    var moods: List<String>? = null,
    var sortName: String? = null,
    var originalReleaseDate: ItemDate? = null,
    var releaseDate: ItemDate? = null,
    var isCompilation: Boolean? = null,
    var discTitles: List<DiscTitle>? = null,
) : Parcelable {

    /**
     * #688: a copy carrying only the lightweight fields, dropping the heavy nested
     * lists (recordLabels/genres/artists/releaseTypes/moods). Pass this into navigation
     * arguments so deep browsing doesn't bloat the saved-state Bundle past the Binder
     * limit (TransactionTooLargeException on background). Detail pages re-fetch the full
     * album, so nothing visible is lost -- except discTitles, which is kept here: it is
     * small and the album song list reads it to render per-disc headers, which a multi-disc
     * album would otherwise lose for good if that re-fetch fails (offline / server error).
     */
    fun strippedForNav(): AlbumID3 = AlbumID3(
        id = id,
        name = name,
        artist = artist,
        artistId = artistId,
        coverArtId = coverArtId,
        songCount = songCount,
        duration = duration,
        playCount = playCount,
        created = created,
        starred = starred,
        year = year,
        genre = genre,
        played = played,
        userRating = userRating,
        musicBrainzId = musicBrainzId,
        displayArtist = displayArtist,
        sortName = sortName,
        originalReleaseDate = originalReleaseDate,
        releaseDate = releaseDate,
        isCompilation = isCompilation,
        discTitles = discTitles,
    )
}