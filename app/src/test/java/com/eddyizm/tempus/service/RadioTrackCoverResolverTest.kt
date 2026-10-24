package com.eddyizm.tempus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioTrackCoverResolverTest {

    @Test
    fun `cleanTitle removes radio edit and remaster brackets`() {
        assertEquals("Pull Me Under", cleanTitle("Pull Me Under (Radio Edit)"))
        assertEquals("Song", cleanTitle("Song [2011 Remastered]"))
    }

    @Test
    fun `cleanTitle keeps raw when only brackets`() {
        assertEquals("(Live)", cleanTitle("(Live)"))
    }

    @Test
    fun `luceneEscape removes quotes and backslash`() {
        assertEquals("a b c", luceneEscape("a\"b\\c"))
    }

    @Test
    fun `musicBrainzReleaseIds picks studio recording and orders album first`() {
        val json = """
            {"recordings":[
              {"id":"live","releases":[
                {"id":"l1","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Live"]}}]},
              {"id":"demo","releases":[
                {"id":"d1","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Demo"]}},
                {"id":"d2","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Demo"]}}]},
              {"id":"studio","releases":[
                {"id":"s1","status":"Official","release-group":{"primary-type":"Single"}},
                {"id":"a1","status":"Official","release-group":{"primary-type":"Album"}},
                {"id":"b1","status":"Bootleg","release-group":{"primary-type":"Album"}},
                {"id":"c1","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Compilation"]}}]}
            ]}
        """.trimIndent()
        assertEquals(listOf("a1", "s1"), musicBrainzReleaseIds(json))
    }

    @Test
    fun `musicBrainzReleaseIds returns empty for error json`() {
        val json = """{"error":"The MusicBrainz web server is currently busy. Please try again later."}"""
        assertTrue(musicBrainzReleaseIds(json).isEmpty())
    }

    @Test
    fun `deezerCover rejects other artist`() {
        val other = """{"data":[{"artist":{"name":"Other"},"album":{"cover_xl":"https://x/other.jpg"}}]}"""
        assertNull(deezerCover(other, "Dream Theater"))
        val second = """{"data":[
            {"artist":{"name":"Other"},"album":{"cover_xl":"https://x/other.jpg"}},
            {"artist":{"name":"dream theater"},"album":{"cover_xl":"https://x/dt.jpg"}}]}"""
        assertEquals("https://x/dt.jpg", deezerCover(second, "Dream Theater"))
    }

    @Test
    fun `itunesCover upsizes artwork and rejects other artist`() {
        val json = """{"results":[
            {"artistName":"Other","artworkUrl100":"https://x/o/100x100bb.jpg"},
            {"artistName":"Dream Theater","artworkUrl100":"https://x/dt/100x100bb.jpg"}]}"""
        assertEquals("https://x/dt/600x600bb.jpg", itunesCover(json, "Dream Theater"))
        val onlyOther = """{"results":[{"artistName":"Other","artworkUrl100":"https://x/o/100x100bb.jpg"}]}"""
        assertNull(itunesCover(onlyOther, "Dream Theater"))
    }
}
