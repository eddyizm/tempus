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
}
