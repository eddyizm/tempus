package com.eddyizm.tempus.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerHeadersTest {
    private val serverUrl = "https://music.example.com"
    private val registered = ArrayList<HeaderSource>()

    private fun server(customHeaders: String? = null) =
        ServerContext(addresses = listOf(serverUrl, "http://192.168.1.5:4533"), customHeaders = customHeaders)

    private fun register(source: HeaderSource): HeaderSource {
        registered.add(source)
        ServerHeaders.register(source)
        return source
    }

    @After
    fun tearDown() {
        registered.forEach { ServerHeaders.unregister(it) }
    }

    @Test
    fun withoutSources_equalsParsedCustomHeaders() {
        val raw = "X-A: 1\nCF-Access-Client-Id: abc"
        assertEquals(CustomHeaders.parse(raw), ServerHeaders.forUrl("$serverUrl/rest/ping", server(raw)))
    }

    @Test
    fun onlyForMatchingOrigin() {
        val raw = "X-A: 1"
        assertEquals(mapOf("X-A" to "1"), ServerHeaders.forUrl("$serverUrl/x", server(raw)))
        assertEquals(mapOf("X-A" to "1"), ServerHeaders.forUrl("http://192.168.1.5:4533/x", server(raw)))
        assertTrue(ServerHeaders.forUrl("https://other.example.com/x", server(raw)).isEmpty())
        assertTrue(ServerHeaders.forUrl("$serverUrl/x", server(null)).isEmpty())
    }

    @Test
    fun registeredSource_isMergedWithCustomHeaders() {
        register { mapOf("Authorization" to "Basic dTpw") }
        assertEquals(
            mapOf("Authorization" to "Basic dTpw", "X-A" to "1"),
            ServerHeaders.forUrl("$serverUrl/rest/ping", server("X-A: 1"))
        )
        // Works without any custom headers too.
        assertEquals(mapOf("Authorization" to "Basic dTpw"), ServerHeaders.forUrl("$serverUrl/rest/ping", server()))
    }

    @Test
    fun sourceReceivesServerContext() {
        var seen: ServerContext? = null
        register { seen = it; emptyMap() }
        val ctx = server("X-A: 1")
        ServerHeaders.forUrl("$serverUrl/x", ctx)
        assertEquals(ctx, seen)
    }

    @Test
    fun laterSourceWins_caseInsensitive() {
        register { mapOf("X-Token" to "first", "X-Keep" to "1") }
        register { mapOf("x-token" to "second") }
        val headers = ServerHeaders.forUrl("$serverUrl/x", server())
        assertEquals(mapOf("X-Keep" to "1", "x-token" to "second"), headers)
    }

    @Test
    fun customHeadersOverrideSources_caseInsensitive() {
        register { mapOf("authorization" to "Basic from-source") }
        val headers = ServerHeaders.forUrl("$serverUrl/x", server("Authorization: Bearer from-user"))
        assertEquals(mapOf("Authorization" to "Bearer from-user"), headers)
    }

    @Test
    fun sourcesAreNotCalledForOtherOrigins() {
        var calls = 0
        register { calls++; mapOf("Authorization" to "Basic dTpw") }
        assertTrue(ServerHeaders.forUrl("https://radio.example.org/stream", server()).isEmpty())
        assertTrue(ServerHeaders.forUrl("http://music.example.com/rest", server()).isEmpty())
        assertTrue(ServerHeaders.forUrl(null, server()).isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun invalidHeadersFromSourceAreDropped() {
        register {
            mapOf(
                "Host" to "evil",
                "Content-Length" to "1",
                "Bad Name" to "x",
                "X-Newline" to "a\r\nX-Injected: 1",
                "X-Unicode" to "caf\u00e9",
                "X-Ok" to "fine"
            )
        }
        assertEquals(mapOf("X-Ok" to "fine"), ServerHeaders.forUrl("$serverUrl/x", server()))
    }

    @Test
    fun failingOrNullSourceIsSkipped() {
        register { throw IllegalStateException("boom") }
        register { null }
        register { mapOf("X-Ok" to "1") }
        assertEquals(mapOf("X-Ok" to "1", "X-A" to "2"), ServerHeaders.forUrl("$serverUrl/x", server("X-A: 2")))
    }

    @Test
    fun unregister_removesSource() {
        val source = register { mapOf("X-Ok" to "1") }
        ServerHeaders.unregister(source)
        assertTrue(ServerHeaders.forUrl("$serverUrl/x", server()).isEmpty())
    }

    private fun withLocal(localAddress: String?, raw: String? = "X-A: 1") =
        ServerContext(addresses = listOf(serverUrl), customHeaders = raw, localAddress = localAddress)

    @Test
    fun plainHttpLocalAddress_getsNoHeaders() {
        var calls = 0
        register { calls++; mapOf("Authorization" to "Basic dTpw") }
        val ctx = withLocal("http://192.168.1.5:4533")
        assertTrue(ServerHeaders.forUrl("http://192.168.1.5:4533/rest/ping", ctx).isEmpty())
        assertEquals(0, calls)
        // The public address is unaffected.
        assertEquals(
            mapOf("Authorization" to "Basic dTpw", "X-A" to "1"),
            ServerHeaders.forUrl("$serverUrl/rest/ping", ctx)
        )
    }

    @Test
    fun httpsLocalAddress_getsHeaders() {
        val ctx = withLocal("https://nas.lan:4533")
        assertEquals(mapOf("X-A" to "1"), ServerHeaders.forUrl("https://nas.lan:4533/rest/ping", ctx))
        // Same host over http is a different origin and gets nothing.
        assertTrue(ServerHeaders.forUrl("http://nas.lan:4533/rest/ping", ctx).isEmpty())
    }

    @Test
    fun localAddressEqualToPublic_isTreatedAsPublic() {
        val publicHttp = "http://192.168.1.5:4533"
        val ctx = ServerContext(addresses = listOf(publicHttp), customHeaders = "X-A: 1", localAddress = publicHttp)
        assertEquals(mapOf("X-A" to "1"), ServerHeaders.forUrl("$publicHttp/rest/ping", ctx))
    }

    @Test
    fun blankOrInvalidLocalAddress_isIgnored() {
        assertTrue(ServerHeaders.forUrl("https://nas.lan/rest", withLocal("")).isEmpty())
        assertTrue(ServerHeaders.forUrl("https://nas.lan/rest", withLocal("not a url")).isEmpty())
        assertEquals(mapOf("X-A" to "1"), ServerHeaders.forUrl("$serverUrl/rest", withLocal("")))
    }
}
