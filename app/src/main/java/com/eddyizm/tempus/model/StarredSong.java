package com.eddyizm.tempus.model;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Keep
@Entity(tableName = "starred_song")
public class StarredSong {
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    @NonNull
    private String songId;

    @ColumnInfo(name = "starred_date")
    private long starredDate;

    public StarredSong() {}

    public StarredSong(String songId, long starredDate) {
        this.songId = songId;
        this.starredDate = starredDate;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public long getStarredDate() {
        return starredDate;
    }

    public void setStarredDate(long starredDate) {
        this.starredDate = starredDate;
    }
}
