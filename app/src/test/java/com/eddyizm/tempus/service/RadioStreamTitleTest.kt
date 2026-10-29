package com.eddyizm.tempus.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioStreamTitleTest {

    @Test
    fun `artist dash title is split`() {
        assertEquals("Marillion" to "Incubus", parseStreamTitle("Marillion - Incubus"))
    }

    @Test
    fun `only the first dash splits`() {
        assertEquals("Artist" to "Song - Part 2", parseStreamTitle("Artist - Song - Part 2"))
    }

    @Test
    fun `title without separator has no artist`() {
        assertEquals(null to "Station jingle", parseStreamTitle("Station jingle"))
    }

    @Test
    fun `tilde format puts title first and artist second`() {
        val raw = "Tiny Dancer [Remastered]~Elton John~~1972~~369~2026-10-23T21:01:15~2026-10-23T21:06:19~Virgin Radio Rock 70~304.98~7191615f-fc56-4371-b214-cfc534201a7b"
        assertEquals("Elton John" to "Tiny Dancer [Remastered]", parseStreamTitle(raw))
    }

    @Test
    fun `tilde format with a slash in the artist`() {
        val raw = "Back In Black~AC/DC~~1980~~209~2026-10-23T21:04:33~2026-10-23T21:05:36~Virgin Radio Classic Rock~63.28~ed836f4e"
        assertEquals("AC/DC" to "Back In Black", parseStreamTitle(raw))
    }

    @Test
    fun `a stray tilde in a title is not the tilde format`() {
        assertEquals(null to "Song ~ live ~ edit", parseStreamTitle("Song ~ live ~ edit"))
        assertEquals(null to "A~B", parseStreamTitle("A~B"))
    }

    @Test
    fun `tilde format with an empty artist is not used`() {
        assertEquals(null to "Jingle~~~2026", parseStreamTitle("Jingle~~~2026"))
    }

    @Test
    fun `blank input gives nothing`() {
        assertEquals(null to null, parseStreamTitle("   "))
    }

    // ---- real strings from a scan of 156 rock/metal/prog stations in 40 countries ----

    @Test
    fun `station name before the dash is a slogan, not an artist`() {
        assertEquals(null to "Style Rock", parseStreamTitle("Virgin Radio - Style Rock", "Virgin Radio Italia"))
        assertEquals(null to "PLAY_8_NEW", parseStreamTitle("50s 60s RETRO HITS - PLAY_8_NEW", " 50s 60s RETRO HITS"))
        assertEquals(
            null to "Comedy Of Errors / The Answer",
            parseStreamTitle("Progressive Rock - Comedy Of Errors / The Answer", "Purple Piper - Progressive Rock")
        )
    }

    @Test
    fun `station name prefix is dropped and the rest is parsed`() {
        assertEquals("JACE EVERETT" to "BAD THINGS", parseStreamTitle("RockFM - JACE EVERETT - BAD THINGS", "RockFM"))
    }

    @Test
    fun `title artist title repeats the title at the end`() {
        val raw = "Opus Interstellar (ANGEMI Remix) - Eric Prydz, Hans Zimmer - Opus Interstellar (ANGEMI Remix)"
        assertEquals(
            "Eric Prydz, Hans Zimmer" to "Opus Interstellar (ANGEMI Remix)",
            parseStreamTitle(raw, "TotalRock Radio")
        )
    }

    @Test
    fun `an artist unrelated to the station is kept`() {
        assertEquals("Motorhead" to "Back At The Funny Farm", parseStreamTitle("Motorhead - Back At The Funny Farm", "Radio Furgoncino"))
    }

    @Test
    fun `a very short name inside the station name is not a slogan`() {
        assertEquals("U2" to "One", parseStreamTitle("U2 - One", "U2 Radio"))
    }

    @Test
    fun `without a station name a slogan cannot be told from an artist`() {
        assertEquals("Virgin Radio" to "Style Rock", parseStreamTitle("Virgin Radio - Style Rock"))
    }

    @Test
    fun `em dash separates like a hyphen`() {
        assertEquals("Artist" to "Title", parseStreamTitle("Artist \u2014 Title"))
        assertEquals("Artist" to "Title", parseStreamTitle("Artist \u2013 Title"))
        assertEquals(
            null to "Guns N' Roses \"You Can't Put Your Arms Around A Memory\"",
            parseStreamTitle("Deecibel Rock Radio  \u2014  Guns N' Roses \"You Can't Put Your Arms Around A Memory\"", "Deecibel Rock Radio")
        )
    }

    @Test
    fun `a trailing year is not part of the title`() {
        assertEquals(
            "John Travolta" to "Greased lightnin'",
            parseStreamTitle("John Travolta - Greased lightnin' - 1978", "70 80 Hits HQ")
        )
    }

    @Test
    fun `a title that is a year is kept`() {
        assertEquals("Prince" to "1999", parseStreamTitle("Prince - 1999"))
    }

    @Test
    fun `extra spaces around the dash are ignored`() {
        assertEquals("Tool" to "Vicarious", parseStreamTitle("Tool   - Vicarious "))
    }
}
