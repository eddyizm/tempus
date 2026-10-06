package com.eddyizm.tempus.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.function.Consumer

/**
 * User-defined HTTP headers for a server (for example Cloudflare Access service tokens).
 *
 * Stored as raw text, one "Name: Value" per line. Headers are only ever sent to the server they
 * belong to: same scheme, host and port as its public or local address. They are never sent to
 * radio stations, other hosts or UPnP renderers, and every redirect hop is checked again.
 */
object CustomHeaders {
    private val NAME = Regex("^[!#\$%&'*+.^_`|~0-9A-Za-z-]+\$")
    private val FORBIDDEN = setOf("host", "content-length", "transfer-encoding", "connection")

    /** Most redirects [openConnectionForActiveServer] follows, like browsers and OkHttp. */
    private const val MAX_REDIRECTS = 20

    /** Valid headers in input order. Invalid, blank and '#' lines are skipped. */
    @JvmStatic
    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (line in raw.lines()) {
            val entry = parseLine(line) ?: continue
            out[entry.first] = entry.second
        }
        return out
    }

    /** 1-based numbers of lines that are not blank, not comments and not valid headers. */
    @JvmStatic
    fun invalidLineNumbers(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val bad = ArrayList<Int>()
        raw.lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
            if (parseLine(line) == null) bad.add(index + 1)
        }
        return bad
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val colon = trimmed.indexOf(':')
        if (colon <= 0) return null
        val name = trimmed.substring(0, colon).trim()
        val value = trimmed.substring(colon + 1).trim()
        if (!NAME.matches(name)) return null
        if (name.lowercase() in FORBIDDEN) return null
        // OkHttp only accepts TAB and printable ASCII in header values.
        if (value.any { (it.code < 0x20 && it != '\t') || it.code > 0x7e }) return null
        return name to value
    }

    /** True when [url] has the same scheme, host and port as one of [serverAddresses]. */
    @JvmStatic
    fun isSameOrigin(url: String?, serverAddresses: Collection<String?>): Boolean {
        val target = url?.trim()?.toHttpUrlOrNull() ?: return false
        return isSameOrigin(target, serverAddresses)
    }

    private fun isSameOrigin(target: HttpUrl, serverAddresses: Collection<String?>): Boolean =
        serverAddresses.any { address ->
            val server = address?.trim()?.toHttpUrlOrNull() ?: return@any false
            server.scheme == target.scheme && server.host == target.host && server.port == target.port
        }

    /** Headers from [raw] if [url] belongs to one of [serverAddresses], otherwise empty. */
    @JvmStatic
    fun forUrl(url: String?, raw: String?, serverAddresses: Collection<String?>): Map<String, String> =
        if (isSameOrigin(url, serverAddresses)) parse(raw) else emptyMap()

    /** Headers of the signed-in server if [url] points at it, otherwise empty. */
    @JvmStatic
    fun forActiveServer(url: String?): Map<String, String> {
        val raw = Preferences.getCustomHeaders()
        if (raw.isNullOrBlank()) return emptyMap()
        return forUrl(
            url,
            raw,
            listOf(
                Preferences.getInUseServerAddress(),
                Preferences.getServer(),
                Preferences.getLocalAddress()
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
}
