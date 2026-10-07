package com.eddyizm.tempus.util

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomHeadersInterceptorTest {

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

    private val raw = "X-Tempus-Test: letmein\nCF-Access-Client-Id: abc"
    private val server = ServerContext(addresses = listOf("https://music.example.com"), customHeaders = raw)
    private val interceptor = CustomHeadersInterceptor { url -> ServerHeaders.forUrl(url, server) }

    private fun run(url: String, existing: Map<String, String> = emptyMap()): Request {
        val builder = Request.Builder().url(url)
        existing.forEach { (name, value) -> builder.header(name, value) }
        val chain = RecordingChain(builder.build())
        interceptor.intercept(chain)
        return chain.proceeded!!
    }

    @Test
    fun addsHeadersForServerOrigin() {
        val request = run("https://music.example.com/rest/ping.view?f=json")
        assertEquals("letmein", request.header("X-Tempus-Test"))
        assertEquals("abc", request.header("CF-Access-Client-Id"))
    }

    @Test
    fun leavesOtherHostsAlone() {
        val request = run("https://radio.example.org/stream")
        assertNull(request.header("X-Tempus-Test"))
        assertNull(request.header("CF-Access-Client-Id"))
    }

    @Test
    fun redirectHopToOtherHostGetsNoHeaders() {
        // OkHttp runs network interceptors again for each redirect hop with the new URL.
        assertEquals("letmein", run("https://music.example.com/rest/stream?id=1").header("X-Tempus-Test"))
        assertNull(run("https://cdn.example.net/file.mp3").header("X-Tempus-Test"))
        assertNull(run("http://music.example.com/rest/stream?id=1").header("X-Tempus-Test"))
    }

    @Test
    fun replacesExistingValueOfSameName() {
        val request = run("https://music.example.com/x", mapOf("X-Tempus-Test" to "old"))
        assertEquals(listOf("letmein"), request.headers("X-Tempus-Test"))
    }
}
