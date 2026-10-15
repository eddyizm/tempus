package com.eddyizm.tempus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MoveToAddressTest {
    private val local = "http://192.168.0.10:4533"
    private val public = "https://music.example.invalid/navidrome"
    private val addresses = listOf(local, public)

    @Test
    fun movesALocalStreamOntoThePublicAddress() {
        assertEquals("$public/rest/stream?u=a&id=1",
            ConnectionUtil.moveToAddress("$local/rest/stream?u=a&id=1", addresses, public))
    }

    @Test
    fun movesAPublicStreamOntoTheLocalAddress() {
        assertEquals("$local/rest/stream?id=1",
            ConnectionUtil.moveToAddress("$public/rest/stream?id=1", addresses, "$local/"))
    }

    @Test
    fun leavesAStreamAlreadyOnTheAddressInUse() {
        val url = "$public/rest/stream?id=1"
        assertSame(url, ConnectionUtil.moveToAddress(url, addresses, public))
    }

    @Test
    fun leavesAnUrlOnNeitherAddress() {
        val url = "https://radio.example.invalid/stream"
        assertSame(url, ConnectionUtil.moveToAddress(url, addresses, public))
        assertSame(url, ConnectionUtil.moveToAddress(url, addresses, null))
    }

    @Test
    fun leavesAnUrlThatOnlySharesAPrefixWithAnAddress() {
        val url = "http://nas2/rest/stream?id=1"
        assertSame(url, ConnectionUtil.moveToAddress(url, listOf("http://nas", public), public))
    }

    @Test
    fun movesAStreamUnderAnAddressSavedWithATrailingSlash() {
        assertEquals("$public/rest/stream?id=1",
            ConnectionUtil.moveToAddress("$local/rest/stream?id=1", listOf("$local/", public), public))
    }

    @Test
    fun skipsAnAddressThatIsNotSet() {
        val url = "$public/rest/stream?id=1"
        assertSame(url, ConnectionUtil.moveToAddress(url, listOf(null, public), public))
    }

    @Test
    fun picksTheLongestAddressAUrlIsUnder() {
        val local = "https://example.invalid"
        val nested = "https://example.invalid/navidrome"
        assertEquals("$local/rest/stream?id=1",
            ConnectionUtil.moveToAddress("$nested/rest/stream?id=1", listOf(local, nested), local))
        assertEquals("$local/rest/stream?id=1",
            ConnectionUtil.moveToAddress("$nested/rest/stream?id=1", listOf(nested, local), local))
    }

    @Test
    fun leavesAnUrlAlreadyUnderANestedInUseAddress() {
        val url = "https://example.invalid/navidrome/rest/stream?id=1"
        assertSame(url, ConnectionUtil.moveToAddress(url,
            listOf("https://example.invalid", "https://example.invalid/navidrome"), "https://example.invalid/navidrome"))
    }
}
