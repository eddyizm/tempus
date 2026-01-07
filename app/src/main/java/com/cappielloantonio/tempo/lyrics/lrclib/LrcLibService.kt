package com.cappielloantonio.tempo.lyrics.lrclib

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibService {
    @GET("get")
    fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String?,
        @Query("duration") duration: Int?
    ): Call<LrcLibResponse>
}

