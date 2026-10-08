package com.eddyizm.tempus.util

import android.util.Log
import com.eddyizm.tempus.model.ServerContext
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Adds headers to requests sent to the signed-in server, for example `Authorization` for basic
 * auth. Register one with [ServerHeaders.register].
 *
 * A source is only called for URLs on the server's own origin. Whatever it returns is validated
 * like user-entered custom headers, so it doesn't need to repeat those checks.
 */
fun interface HeaderSource {
    fun getHeaders(server: ServerContext): Map<String, String>?
}

/**
 * Works out which headers a request to the signed-in server gets, and attaches them.
 *
 * Headers from every registered [HeaderSource] (e.g. basic auth) are merged first, in registration
 * order. The user's custom headers are applied last, so they override anything else. Header
 * names are compared case-insensitively.
 *
 * Every request path asks here, so a new source applies everywhere and gets the same guarantees as
 * custom headers:
 * - nothing is sent to other hosts, and each redirect hop is checked again;
 * - invalid names or values are dropped instead of sent;
 * - a source that throws is skipped, the request still goes out.
 *
 * Headers are attached either by [Interceptor] (Retrofit, Glide, ExoPlayer, all OkHttp) or by
 * [openConnectionForActiveServer] (external downloads, `HttpURLConnection`).
 *
 * Nothing is persisted: headers are computed per request and the stored server is never modified.
 */
object ServerHeaders {
    private const val TAG = "ServerHeaders"

    /** Most redirects [openConnectionForActiveServer] follows, like browsers and OkHttp. */
    private const val MAX_REDIRECTS = 20

    private val sources = CopyOnWriteArrayList<HeaderSource>()

    @JvmStatic
    fun register(source: HeaderSource) {
        sources.addIfAbsent(source)
    }

    @JvmStatic
    fun unregister(source: HeaderSource) {
        sources.remove(source)
    }

    /** Merged headers for [url] if it belongs to [server], otherwise empty. */
    @JvmStatic
    fun getHeadersForUrl(url: String?, server: ServerContext): Map<String, String> {
        if (sources.isEmpty() && server.customHeaders.isNullOrBlank()) return emptyMap()
        if (!CustomHeaders.isServerOrigin(url, server.addresses)) return emptyMap()

        val merged = LinkedHashMap<String, String>()
        for (source in sources) {
            val headers = try {
                source.getHeaders(server)
            } catch (e: Exception) {
                Log.w(TAG, "Header source ${source.javaClass.name} failed, skipping it", e)
                null
            } ?: continue
            for ((name, value) in headers) {
                if (CustomHeaders.isValid(name, value)) {
                    putIgnoringCase(merged, name, value)
                } else {
                    Log.w(TAG, "Dropping invalid header \"$name\" from ${source.javaClass.name}")
                }
            }
        }
        // Already validated by parse(). Applied last so the user's custom headers override sources.
        CustomHeaders.parse(server.customHeaders).forEach { (name, value) -> putIgnoringCase(merged, name, value) }
        return merged
    }

    /** Headers of the signed-in server if [url] points at it, otherwise empty. */
    @JvmStatic
    fun getHeadersForActiveServer(url: String?): Map<String, String> {
        val raw = Preferences.getCustomHeaders()
        if (sources.isEmpty() && raw.isNullOrBlank()) return emptyMap()
        return getHeadersForUrl(
            url,
            ServerContext(
                addresses = listOf(
                    Preferences.getInUseServerAddress(),
                    Preferences.getServer(),
                    Preferences.getLocalAddress()
                ),
                customHeaders = raw
            )
        )
    }

    /** OkHttp interceptor adding the signed-in server's headers, looked up on every request. */
    @JvmStatic
    fun createInterceptor(): Interceptor = Interceptor(::getHeadersForActiveServer)

    /** OkHttp interceptor adding [server]'s headers, for a client bound to one server. */
    @JvmStatic
    fun createInterceptor(server: ServerContext): Interceptor = Interceptor { url -> getHeadersForUrl(url, server) }

    /**
     * Opens [url] with the signed-in server's headers when it points at that server.
     *
     * [HttpURLConnection] would carry request headers across a redirect to another host, so when
     * headers are attached, redirects are followed here instead and every hop is checked again.
     * [configure] is applied to each connection before it connects.
     */
    @JvmStatic
    fun openConnectionForActiveServer(url: String, configure: Consumer<HttpURLConnection>): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) {
            val headers = getHeadersForActiveServer(current.toString())
            val connection = current.openConnection() as HttpURLConnection
            configure.accept(connection)
            if (headers.isEmpty()) return connection
            connection.instanceFollowRedirects = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            if (code !in 300..399 || code == HttpURLConnection.HTTP_NOT_MODIFIED || location == null) {
                return connection
            }
            connection.disconnect()
            current = URL(current, location)
        }
        throw IOException("Too many redirects: $url")
    }

    /** Puts [name] into [map], replacing an existing entry whose name differs only in case. */
    private fun putIgnoringCase(map: MutableMap<String, String>, name: String, value: String) {
        map.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let { map.remove(it) }
        map[name] = value
    }

    /**
     * Adds the headers returned by [getHeaders] for the request URL to OkHttp requests.
     *
     * Install it with `addNetworkInterceptor`: a network interceptor runs once per network request,
     * including every redirect hop, so [getHeaders] is asked again for each URL and the headers do
     * not follow a redirect to another host. It also runs after application interceptors such as
     * `HttpLoggingInterceptor`, so the values are not logged.
     */
    class Interceptor(
        private val getHeaders: (url: String) -> Map<String, String>
    ) : okhttp3.Interceptor {

        override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
            val request = chain.request()
            val headers = getHeaders(request.url.toString())
            if (headers.isEmpty()) return chain.proceed(request)

            val builder = request.newBuilder()
            headers.forEach { (name, value) -> builder.header(name, value) }
            return chain.proceed(builder.build())
        }
    }
}
