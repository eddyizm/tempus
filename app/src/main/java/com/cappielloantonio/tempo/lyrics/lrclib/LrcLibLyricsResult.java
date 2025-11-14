package com.cappielloantonio.tempo.lyrics.lrclib;

import androidx.annotation.Nullable;

import com.cappielloantonio.tempo.subsonic.models.LyricsList;

public class LrcLibLyricsResult {
    private final LyricsList lyricsList;
    private final String plainLyrics;

    public LrcLibLyricsResult(@Nullable LyricsList lyricsList, @Nullable String plainLyrics) {
        this.lyricsList = lyricsList;
        this.plainLyrics = plainLyrics;
    }

    @Nullable
    public LyricsList getLyricsList() {
        return lyricsList;
    }

    @Nullable
    public String getPlainLyrics() {
        return plainLyrics;
    }
}

