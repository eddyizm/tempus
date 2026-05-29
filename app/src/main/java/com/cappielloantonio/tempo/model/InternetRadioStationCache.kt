package com.cappielloantonio.tempo.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation

@Keep
@Entity(tableName = "internet_radio_station_cache")
class InternetRadioStationCache(
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: String = "",
    @ColumnInfo(name = "name")
    var name: String? = null,
    @ColumnInfo(name = "stream_url")
    var streamUrl: String? = null,
    @ColumnInfo(name = "home_page_url")
    var homePageUrl: String? = null,
    @ColumnInfo(name = "cover_art")
    var coverArtId: String? = null,
) {
    constructor(station: InternetRadioStation) : this(
        id = station.id ?: "",
        name = station.name,
        streamUrl = station.streamUrl,
        homePageUrl = station.homePageUrl,
        coverArtId = station.coverArtId,
    )

    fun toInternetRadioStation(): InternetRadioStation {
        return InternetRadioStation(
            id = id,
            name = name,
            streamUrl = streamUrl,
            homePageUrl = homePageUrl,
            coverArtId = coverArtId,
        )
    }
}
