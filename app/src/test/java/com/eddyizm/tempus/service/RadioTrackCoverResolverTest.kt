package com.eddyizm.tempus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
              {"id":"live","score":100,"title":"Pull Me Under","artist-credit":[{"name":"Dream Theater"}],"releases":[
                {"id":"l1","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Live"]}}]},
              {"id":"demo","score":100,"title":"Pull Me Under","artist-credit":[{"name":"Dream Theater"}],"releases":[
                {"id":"d1","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Demo"]}},
                {"id":"d2","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Demo"]}}]},
              {"id":"studio","score":100,"title":"Pull Me Under","artist-credit":[{"name":"Dream Theater"}],"releases":[
                {"id":"s1","status":"Official","release-group":{"primary-type":"Single"}},
                {"id":"a1","status":"Official","release-group":{"primary-type":"Album"}},
                {"id":"b1","status":"Bootleg","release-group":{"primary-type":"Album"}},
                {"id":"c1","status":"Official","release-group":{"primary-type":"Album","secondary-types":["Compilation"]}}]}
            ]}
        """.trimIndent()
        assertEquals(listOf("a1", "s1"), musicBrainzReleaseIds(json, "Dream Theater", "Pull Me Under"))
    }

    private fun recording(score: Int, title: String, credit: String, vararg releaseIds: String) =
        """{"id":"r-$title-$credit","score":$score,"title":"$title","artist-credit":[{"name":"$credit"}],"releases":[""" +
            releaseIds.joinToString(",") { """{"id":"$it","status":"Official","release-group":{"primary-type":"Album"}}""" } +
            "]}"

    @Test
    fun `musicBrainzReleaseIds ignores a similarly named artist`() {
        val json = """{"recordings":[""" +
            recording(100, "Hotel California", "Eagles of Death Metal", "x1", "x2", "x3") + "," +
            recording(100, "Hotel California", "Eagles", "e1") + "]}"
        assertEquals(listOf("e1"), musicBrainzReleaseIds(json, "Eagles", "Hotel California"))
    }

    @Test
    fun `musicBrainzReleaseIds ignores a recording with another title`() {
        val json = """{"recordings":[""" + recording(100, "Hotel California (Live)", "Eagles", "l1") + "]}"
        assertTrue(musicBrainzReleaseIds(json, "Eagles", "Hotel California").isEmpty())
    }

    @Test
    fun `musicBrainzReleaseIds ignores a low score recording`() {
        val json = """{"recordings":[""" + recording(60, "Hotel California", "Eagles", "e1") + "]}"
        assertTrue(musicBrainzReleaseIds(json, "Eagles", "Hotel California").isEmpty())
    }

    @Test
    fun `musicBrainzReleaseIds returns empty for error json`() {
        val json = """{"error":"The MusicBrainz web server is currently busy. Please try again later."}"""
        assertTrue(musicBrainzReleaseIds(json, "Dream Theater", "Pull Me Under").isEmpty())
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

    @Test
    fun `isSameRadioSong is true only for the same radio item and song`() {
        assertTrue(isSameRadioSong("st1", "st1", true, "Marillion", "Incubus", "Marillion", "Incubus"))
        // user started another station while the lookup ran
        assertFalse(isSameRadioSong("st2", "st1", true, "Marillion", "Incubus", "Marillion", "Incubus"))
        // user started a normal track
        assertFalse(isSameRadioSong("track9", "st1", false, null, null, "Marillion", "Incubus"))
        // the song changed
        assertFalse(isSameRadioSong("st1", "st1", true, "Marillion", "Fugazi", "Marillion", "Incubus"))
    }

    // ---- RadioTrackCoverResolver (fake network) ----

    private fun mbJson(vararg ids: String) =
        """{"recordings":[{"id":"rec","score":100,"title":"Pull Me Under","artist-credit":[{"name":"Dream Theater"}],"releases":[""" +
            ids.joinToString(",") { """{"id":"$it","status":"Official","release-group":{"primary-type":"Album"}}""" } +
            "]}]}"

    private val noMusicBrainz = """{"recordings":[]}"""
    private fun deezerJson(artist: String, cover: String) =
        """{"data":[{"artist":{"name":"$artist"},"album":{"cover_xl":"$cover"}}]}"""
    private fun itunesJson(artist: String, cover: String) =
        """{"results":[{"artistName":"$artist","artworkUrl100":"$cover"}]}"""

    private class Fake(
        val musicBrainz: String? = null,
        val deezer: String? = null,
        val itunes: String? = null,
        val archiveHits: Set<String> = emptySet(),
        val failGet: Boolean = false,
    ) {
        val getCalls = mutableListOf<String>()
        val existsCalls = mutableListOf<String>()

        val get: (String) -> String? = { url ->
            getCalls += url
            if (failGet) throw RuntimeException("network down")
            when {
                "musicbrainz.org" in url -> musicBrainz
                "api.deezer.com" in url -> deezer
                "itunes.apple.com" in url -> itunes
                else -> null
            }
        }
        val exists: (String) -> Boolean = { url ->
            existsCalls += url
            archiveHits.any { url.contains("/$it/") }
        }

        fun resolver() = RadioTrackCoverResolver(get, exists)
    }

    @Test
    fun `resolve returns first cover archive hit`() {
        val fake = Fake(musicBrainz = mbJson("r1", "r2", "r3", "r4", "r5", "r6"), archiveHits = setOf("r2"))
        assertEquals(
            "https://coverartarchive.org/release/r2/front-500",
            fake.resolver().resolve("Dream Theater", "Pull Me Under")
        )
        assertEquals(2, fake.existsCalls.size)
    }

    @Test
    fun `resolve tries at most five archive releases then deezer`() {
        val fake = Fake(
            musicBrainz = mbJson("r1", "r2", "r3", "r4", "r5", "r6"),
            deezer = deezerJson("Dream Theater", "https://x/dz.jpg")
        )
        assertEquals("https://x/dz.jpg", fake.resolver().resolve("Dream Theater", "Pull Me Under"))
        assertEquals(5, fake.existsCalls.size)
        assertTrue(fake.getCalls.any { "api.deezer.com" in it })
    }

    @Test
    fun `resolve uses deezer and skips itunes when deezer hits`() {
        val fake = Fake(
            musicBrainz = noMusicBrainz,
            deezer = deezerJson("Dream Theater", "https://x/dz.jpg"),
            itunes = itunesJson("Dream Theater", "https://x/it/100x100bb.jpg")
        )
        assertEquals("https://x/dz.jpg", fake.resolver().resolve("Dream Theater", "Pull Me Under"))
        assertTrue(fake.getCalls.none { "itunes.apple.com" in it })
    }

    @Test
    fun `resolve falls back to itunes when deezer has another artist`() {
        val fake = Fake(
            musicBrainz = noMusicBrainz,
            deezer = deezerJson("Other", "https://x/dz.jpg"),
            itunes = itunesJson("Dream Theater", "https://x/it/100x100bb.jpg")
        )
        assertEquals("https://x/it/600x600bb.jpg", fake.resolver().resolve("Dream Theater", "Pull Me Under"))
    }

    @Test
    fun `resolve survives non json from every service`() {
        val busy = "<html>busy</html>"
        val fake = Fake(musicBrainz = busy, deezer = busy, itunes = busy)
        assertNull(fake.resolver().resolve("Dream Theater", "Pull Me Under"))
    }

    @Test
    fun `resolve survives an error json from musicbrainz and uses deezer`() {
        val fake = Fake(
            musicBrainz = """{"error":"The MusicBrainz web server is currently busy."}""",
            deezer = deezerJson("Dream Theater", "https://x/dz.jpg")
        )
        assertEquals("https://x/dz.jpg", fake.resolver().resolve("Dream Theater", "Pull Me Under"))
    }

    @Test
    fun `resolve returns null when the network throws`() {
        assertNull(Fake(failGet = true).resolver().resolve("Dream Theater", "Pull Me Under"))
    }

    @Test
    fun `resolve does not cache a lookup that failed on the network`() {
        val fake = Fake(failGet = true)
        val resolver = fake.resolver()
        assertNull(resolver.resolve("Dream Theater", "Pull Me Under"))
        val callsAfterFirst = fake.getCalls.size
        assertNull(resolver.resolve("Dream Theater", "Pull Me Under"))
        assertTrue(fake.getCalls.size > callsAfterFirst)
    }

    @Test
    fun `resolve caches found and not found`() {
        val found = Fake(musicBrainz = mbJson("r1"), archiveHits = setOf("r1"))
        val foundResolver = found.resolver()
        val first = foundResolver.resolve("Dream Theater", "Pull Me Under")
        val callsAfterFirst = found.getCalls.size + found.existsCalls.size
        assertEquals(first, foundResolver.resolve("DREAM THEATER", "pull me under"))
        assertEquals(callsAfterFirst, found.getCalls.size + found.existsCalls.size)

        val missing = Fake(musicBrainz = noMusicBrainz, deezer = """{"data":[]}""", itunes = """{"results":[]}""")
        val missingResolver = missing.resolver()
        assertNull(missingResolver.resolve("Nobody", "Nothing"))
        val missingCalls = missing.getCalls.size
        assertNull(missingResolver.resolve("Nobody", "Nothing"))
        assertEquals(missingCalls, missing.getCalls.size)
    }
}
