package com.cappielloantonio.tempo.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.jvm.JvmOverloads

@Keep
@Entity(tableName = "lyrics_cache")
data class LyricsCache @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    var songId: String,
    @ColumnInfo(name = "artist")
    var artist: String? = null,
    @ColumnInfo(name = "title")
    var title: String? = null,
    @ColumnInfo(name = "lyrics")
    var lyrics: String? = null,
    @ColumnInfo(name = "structured_lyrics")
    var structuredLyrics: String? = null,
    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis()
)