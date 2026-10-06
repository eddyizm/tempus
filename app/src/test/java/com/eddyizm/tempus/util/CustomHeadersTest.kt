package com.eddyizm.tempus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomHeadersTest {
    @Test
    fun parse_readsNameValueLines() {
        val raw = "CF-Access-Client-Id: abc\n\n# comment\nX-Token:  s3cr:et  \n"
        assertEquals(
            linkedMapOf("CF-Access-Client-Id" to "abc", "X-Token" to "s3cr:et"),
            CustomHeaders.parse(raw)
        )
    }

    @Test
    fun parse_keepsInputOrder_lastDuplicateWins() {
        val parsed = CustomHeaders.parse("B: 1\nA: 2\nB: 3")
        assertEquals(listOf("B", "A"), parsed.keys.toList())
        assertEquals("3", parsed["B"])
    }

    @Test
    fun parse_acceptsWindowsLineEndingsAndEmptyValue() {
        assertEquals(linkedMapOf("X-A" to "1", "X-Empty" to ""), CustomHeaders.parse("X-A: 1\r\nX-Empty:\r\n"))
    }

    @Test
    fun parse_nullOrBlank_isEmpty() {
        assertTrue(CustomHeaders.parse(null).isEmpty())
        assertTrue(CustomHeaders.parse("  \n ").isEmpty())
    }

    @Test
    fun parse_skipsInvalidAndForbidden() {
        val raw = "no colon\n: novalue-name\nBad Name: x\nHost: evil\ncontent-length: 1\n" +
            "Transfer-Encoding: chunked\nCONNECTION: close\nOk: 1"
        assertEquals(mapOf("Ok" to "1"), CustomHeaders.parse(raw))
    }

    @Test
    fun parse_rejectsControlAndNonAsciiValues() {
        assertTrue(CustomHeaders.parse("X-A: a\u0000b").isEmpty())
        assertTrue(CustomHeaders.parse("X-A: caf\u00e9").isEmpty())
        assertTrue(CustomHeaders.parse("X-A: a\u007fb").isEmpty())
        assertEquals(mapOf("X-A" to "a\tb"), CustomHeaders.parse("X-A: a\tb"))
    }

    @Test
    fun invalidLineNumbers_reportsOneBasedLines() {
        val raw = "Ok: 1\nbroken\n\n# fine\nHost: x"
        assertEquals(listOf(2, 5), CustomHeaders.invalidLineNumbers(raw))
        assertTrue(CustomHeaders.invalidLineNumbers(null).isEmpty())
        assertTrue(CustomHeaders.invalidLineNumbers("X-A: 1\n# c\n").isEmpty())
    }

    @Test
    fun isSameOrigin_matchesSchemeHostPort() {
        val servers = listOf("https://music.example.com", "http://192.168.1.5:4533", null, "not a url")
        assertTrue(CustomHeaders.isSameOrigin("https://music.example.com/rest/stream?id=1", servers))
        assertTrue(CustomHeaders.isSameOrigin("https://music.example.com:443/rest/ping", servers))
        assertTrue(CustomHeaders.isSameOrigin("http://192.168.1.5:4533/rest/ping", servers))
        assertTrue(CustomHeaders.isSameOrigin("HTTPS://MUSIC.example.com/rest", servers))
        assertFalse(CustomHeaders.isSameOrigin("http://music.example.com/rest/ping", servers))
        assertFalse(CustomHeaders.isSameOrigin("https://music.example.com:8443/rest", servers))
        assertFalse(CustomHeaders.isSameOrigin("https://radio.example.org/stream", servers))
        assertFalse(CustomHeaders.isSameOrigin("https://evil.music.example.com/rest", servers))
        assertFalse(CustomHeaders.isSameOrigin("https://music.example.com.evil.org/rest", servers))
        assertFalse(CustomHeaders.isSameOrigin("http://192.168.1.5/rest/ping", servers))
        assertFalse(CustomHeaders.isSameOrigin(null, servers))
        assertFalse(CustomHeaders.isSameOrigin("content://media/1", servers))
    }

    @Test
    fun isSameOrigin_serverWithPath() {
        val servers = listOf("https://example.com/navidrome/")
        assertTrue(CustomHeaders.isSameOrigin("https://example.com/navidrome/rest/ping", servers))
    }

    @Test
    fun forUrl_onlyForMatchingOrigin() {
        val raw = "X-A: 1"
        val servers = listOf("https://music.example.com")
        assertEquals(mapOf("X-A" to "1"), CustomHeaders.forUrl("https://music.example.com/x", raw, servers))
        assertTrue(CustomHeaders.forUrl("https://other.example.com/x", raw, servers).isEmpty())
        assertTrue(CustomHeaders.forUrl("https://music.example.com/x", null, servers).isEmpty())
    }
}
