package com.eddyizm.tempus.service

import com.eddyizm.tempus.subsonic.models.Line
import com.eddyizm.tempus.subsonic.models.LyricsList
import com.eddyizm.tempus.subsonic.models.StructuredLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomotiveLyricsUpdaterTest {

    private fun line(start: Int?, value: String) = Line().apply {
        this.start = start
        this.value = value
    }

    private fun lyricsList(synced: Boolean, lines: List<Line>?) = LyricsList().apply {
        structuredLyrics = listOf(StructuredLyrics().apply {
            this.synced = synced
            this.line = lines
        })
    }

    private val lines = listOf(line(1000, "first"), line(3000, "second"), line(5000, "third"))

    @Test
    fun findLineIndex_beforeFirstLine_returnsMinusOne() {
        assertEquals(-1, AutomotiveLyricsUpdater.findLineIndex(lines, 999))
    }

    @Test
    fun findLineIndex_atLineStart_returnsThatLine() {
        assertEquals(0, AutomotiveLyricsUpdater.findLineIndex(lines, 1000))
        assertEquals(1, AutomotiveLyricsUpdater.findLineIndex(lines, 3000))
    }

    @Test
    fun findLineIndex_betweenLines_returnsPreviousLine() {
        assertEquals(1, AutomotiveLyricsUpdater.findLineIndex(lines, 4999))
    }

    @Test
    fun findLineIndex_afterLastLine_returnsLastLine() {
        assertEquals(2, AutomotiveLyricsUpdater.findLineIndex(lines, 60000))
    }

    @Test
    fun findLineIndex_skipsLinesWithoutStart() {
        val withGap = listOf(line(1000, "first"), line(null, "untimed"), line(3000, "second"))
        assertEquals(0, AutomotiveLyricsUpdater.findLineIndex(withGap, 2000))
        assertEquals(2, AutomotiveLyricsUpdater.findLineIndex(withGap, 3000))
    }

    @Test
    fun formatSongInfo_joinsTitleAndArtist() {
        assertEquals("Song - Artist", AutomotiveLyricsUpdater.formatSongInfo("Song", "Artist"))
    }

    @Test
    fun formatSongInfo_missingPart_returnsTheOther() {
        assertEquals("Artist", AutomotiveLyricsUpdater.formatSongInfo(null, "Artist"))
        assertEquals("Song", AutomotiveLyricsUpdater.formatSongInfo("Song", " "))
        assertNull(AutomotiveLyricsUpdater.formatSongInfo(null, null))
    }

    @Test
    fun getSyncedLines_returnsLinesOfSyncedLyrics() {
        assertEquals(lines, AutomotiveLyricsUpdater.getSyncedLines(lyricsList(true, lines)))
    }

    @Test
    fun getSyncedLines_unsyncedLyrics_returnsNull() {
        assertNull(AutomotiveLyricsUpdater.getSyncedLines(lyricsList(false, lines)))
    }

    @Test
    fun getSyncedLines_missingOrEmptyLyrics_returnsNull() {
        assertNull(AutomotiveLyricsUpdater.getSyncedLines(null))
        assertNull(AutomotiveLyricsUpdater.getSyncedLines(LyricsList()))
        assertNull(AutomotiveLyricsUpdater.getSyncedLines(lyricsList(true, emptyList())))
    }
}
