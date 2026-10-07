package com.eddyizm.tempus.util

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/** The server a request is going to, as seen by a [HeaderSource]. */
data class ServerContext(
    /** Addresses that always get headers (the public address). Requests are only for these origins. */
    val addresses: List<String?>,
    /** The user's raw custom headers text (see [CustomHeaders]). */
    val customHeaders: String?,
    /** The server's local address. Gets headers only over https, see [ServerHeaders.forUrl]. */
    val localAddress: String? = null,
)

/**
 * Adds headers to requests sent to the signed-in server, for example `Authorization` for basic
 * auth. Register one with [ServerHeaders.register].
 *
 * A source is only called for URLs on the server's own origin. Whatever it returns is validated
 * like user-entered custom headers, so it doesn't need to repeat those checks.
 */
fun interface HeaderSource {
    fun headers(server: ServerContext): Map<String, String>?
}

/**
 * Works out which headers a request to the signed-in server gets: the output of every registered
 * [HeaderSource], merged in registration order, with the user's custom headers applied last so they
 * can override anything. Header names are compared case-insensitively.
 *
 * Every request path (Retrofit, Glide, ExoPlayer, external downloads) asks here, so a new source
 * applies everywhere and gets the same guarantees as custom headers:
 * - nothing is sent to other hosts, and each redirect hop is checked again by the callers;
 * - invalid names or values are dropped instead of sent;
 * - a source that throws is skipped, the request still goes out.
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
    fun forUrl(url: String?, server: ServerContext): Map<String, String> {
        if (sources.isEmpty() && server.customHeaders.isNullOrBlank()) return emptyMap()
        if (!isEligible(url, server)) return emptyMap()

        val merged = LinkedHashMap<String, String>()
        for (source in sources) {
            val headers = try {
                source.headers(server)
            } catch (e: Exception) {
                Log.w(TAG, "Header source ${source.javaClass.name} failed, skipping it", e)
                null
            } ?: continue
            for ((name, value) in headers) {
                if (CustomHeaders.isValid(name, value)) {
                    putReplacing(merged, name, value)
                } else {
                    Log.w(TAG, "Dropping invalid header \"$name\" from ${source.javaClass.name}")
                }
            }
        }
        // Already validated by parse(). Applied last so the user's own headers always win.
        CustomHeaders.parse(server.customHeaders).forEach { (name, value) -> putReplacing(merged, name, value) }
        return merged
    }

    /**
     * True when [url] is one of the server's [ServerContext.addresses], or its
     * [ServerContext.localAddress] over https. A plain-http local address could be any machine
     * that answers on that IP on whatever network the phone is on, so it never gets headers.
     */
    private fun isEligible(url: String?, server: ServerContext): Boolean {
        if (CustomHeaders.isSameOrigin(url, server.addresses)) return true
        val local = server.localAddress?.trim()?.toHttpUrlOrNull() ?: return false
        return local.isHttps && CustomHeaders.isSameOrigin(url, listOf(server.localAddress))
    }

    /** Headers of the signed-in server if [url] points at it, otherwise empty. */
    @JvmStatic
    fun forActiveServer(url: String?): Map<String, String> {
        val raw = Preferences.getCustomHeaders()
        if (sources.isEmpty() && raw.isNullOrBlank()) return emptyMap()
        return forUrl(
            url,
            ServerContext(
                addresses = listOf(Preferences.getServer()),
                customHeaders = raw,
                localAddress = Preferences.getLocalAddress()
            )
        )
    }

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
            val headers = forActiveServer(current.toString())
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
    private fun putReplacing(map: MutableMap<String, String>, name: String, value: String) {
        map.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let { map.remove(it) }
        map[name] = value
    }
}
