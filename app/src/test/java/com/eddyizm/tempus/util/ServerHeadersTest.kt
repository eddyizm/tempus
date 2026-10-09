package com.eddyizm.tempus.util

import com.eddyizm.tempus.model.ServerContext
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(CustomHeaders.parse(raw), ServerHeaders.getHeadersForUrl("$serverUrl/rest/ping", server(raw)))
    }

    @Test
    fun onlyForMatchingOrigin() {
        val raw = "X-A: 1"
        assertEquals(mapOf("X-A" to "1"), ServerHeaders.getHeadersForUrl("$serverUrl/x", server(raw)))
        assertEquals(mapOf("X-A" to "1"), ServerHeaders.getHeadersForUrl("http://192.168.1.5:4533/x", server(raw)))
        assertTrue(ServerHeaders.getHeadersForUrl("https://other.example.com/x", server(raw)).isEmpty())
        assertTrue(ServerHeaders.getHeadersForUrl("$serverUrl/x", server(null)).isEmpty())
    }

    @Test
    fun registeredSource_isMergedWithCustomHeaders() {
        register { mapOf("Authorization" to "Basic dTpw") }
        assertEquals(
            mapOf("Authorization" to "Basic dTpw", "X-A" to "1"),
            ServerHeaders.getHeadersForUrl("$serverUrl/rest/ping", server("X-A: 1"))
        )
        // Works without any custom headers too.
        assertEquals(mapOf("Authorization" to "Basic dTpw"), ServerHeaders.getHeadersForUrl("$serverUrl/rest/ping", server()))
    }

    @Test
    fun sourceReceivesServerContext() {
        var seen: ServerContext? = null
        register { seen = it; emptyMap() }
        val ctx = server("X-A: 1")
        ServerHeaders.getHeadersForUrl("$serverUrl/x", ctx)
        assertEquals(ctx, seen)
    }

    @Test
    fun laterSourceWins_caseInsensitive() {
        register { mapOf("X-Token" to "first", "X-Keep" to "1") }
        register { mapOf("x-token" to "second") }
        val headers = ServerHeaders.getHeadersForUrl("$serverUrl/x", server())
        assertEquals(mapOf("X-Keep" to "1", "x-token" to "second"), headers)
    }

    @Test
    fun customHeadersOverrideBasicAuthSource_caseInsensitive() {
        register { mapOf("authorization" to "Basic from-source") }
        val headers = ServerHeaders.getHeadersForUrl("$serverUrl/x", server("Authorization: Bearer from-user"))
        assertEquals(mapOf("Authorization" to "Bearer from-user"), headers)
    }

    @Test
    fun sourcesAreNotCalledForOtherOrigins() {
        var calls = 0
        register { calls++; mapOf("Authorization" to "Basic dTpw") }
        assertTrue(ServerHeaders.getHeadersForUrl("https://radio.example.org/stream", server()).isEmpty())
        assertTrue(ServerHeaders.getHeadersForUrl("http://music.example.com/rest", server()).isEmpty())
        assertTrue(ServerHeaders.getHeadersForUrl(null, server()).isEmpty())
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
        assertEquals(mapOf("X-Ok" to "fine"), ServerHeaders.getHeadersForUrl("$serverUrl/x", server()))
    }

    @Test
    fun failingOrNullSourceIsSkipped() {
        register { throw IllegalStateException("boom") }
        register { null }
        register { mapOf("X-Ok" to "1") }
        assertEquals(mapOf("X-Ok" to "1", "X-A" to "2"), ServerHeaders.getHeadersForUrl("$serverUrl/x", server("X-A: 2")))
    }

    @Test
    fun unregister_removesSource() {
        val source = register { mapOf("X-Ok" to "1") }
        ServerHeaders.unregister(source)
        assertTrue(ServerHeaders.getHeadersForUrl("$serverUrl/x", server()).isEmpty())
    }

    // --- OkHttp interceptor ---

    /** Records the request it is asked to proceed with, and answers 200. */
    private class RecordingChain(private val request: Request) : Interceptor.Chain by unsupported() {
        var proceeded: Request? = null

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            proceeded = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
        }

        companion object {
            private fun unsupported(): Interceptor.Chain =
                java.lang.reflect.Proxy.newProxyInstance(
                    Interceptor.Chain::class.java.classLoader,
                    arrayOf(Interceptor.Chain::class.java)
                ) { _, method, _ -> throw UnsupportedOperationException(method.name) } as Interceptor.Chain
        }
    }

    private val interceptorRaw = "X-Tempus-Test: letmein\nCF-Access-Client-Id: abc"
    private val interceptorServer = ServerContext(addresses = listOf("https://music.example.com"), customHeaders = interceptorRaw)
    private val interceptor = ServerHeaders.Interceptor { url -> ServerHeaders.getHeadersForUrl(url, interceptorServer) }

    private fun run(url: String, existing: Map<String, String> = emptyMap()): Request {
        val builder = Request.Builder().url(url)
        existing.forEach { (name, value) -> builder.header(name, value) }
        val chain = RecordingChain(builder.build())
        interceptor.intercept(chain)
        return chain.proceeded!!
    }

    @Test
    fun interceptor_addsHeadersForServerOrigin() {
        val request = run("https://music.example.com/rest/ping.view?f=json")
        assertEquals("letmein", request.header("X-Tempus-Test"))
        assertEquals("abc", request.header("CF-Access-Client-Id"))
    }

    @Test
    fun interceptor_leavesOtherHostsAlone() {
        val request = run("https://radio.example.org/stream")
        assertNull(request.header("X-Tempus-Test"))
        assertNull(request.header("CF-Access-Client-Id"))
    }

    @Test
    fun interceptor_redirectHopToOtherHostGetsNoHeaders() {
        // OkHttp runs network interceptors again for each redirect hop with the new URL.
        assertEquals("letmein", run("https://music.example.com/rest/stream?id=1").header("X-Tempus-Test"))
        assertNull(run("https://cdn.example.net/file.mp3").header("X-Tempus-Test"))
        assertNull(run("http://music.example.com/rest/stream?id=1").header("X-Tempus-Test"))
    }

    @Test
    fun interceptor_replacesExistingValueOfSameName() {
        val request = run("https://music.example.com/x", mapOf("X-Tempus-Test" to "old"))
        assertEquals(listOf("letmein"), request.headers("X-Tempus-Test"))
    }
}
