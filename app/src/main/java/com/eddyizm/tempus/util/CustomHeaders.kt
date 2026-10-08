package com.eddyizm.tempus.util

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * User-defined HTTP headers for a server (for example Cloudflare Access service tokens).
 *
 * Stored as raw text, one "Name: Value" per line. Headers are only ever sent to the server they
 * belong to: same scheme, host and port as its public or local address. They are never sent to
 * radio stations, other hosts or UPnP renderers, and every redirect hop is checked again.
 *
 * This object parses and validates the text. [ServerHeaders] decides which headers a request
 * gets, merging these with headers from other features.
 */
object CustomHeaders {
    private val NAME = Regex("^[!#\$%&'*+.^_`|~0-9A-Za-z-]+\$")
    private val FORBIDDEN = setOf("host", "content-length", "transfer-encoding", "connection")

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
        return if (isValid(name, value)) name to value else null
    }

    /** True when [name] is an allowed header name and [value] is safe to send. */
    @JvmStatic
    fun isValid(name: String?, value: String?): Boolean {
        if (name == null || value == null) return false
        if (!NAME.matches(name)) return false
        if (name.lowercase() in FORBIDDEN) return false
        // OkHttp only accepts TAB and printable ASCII in header values.
        if (value.any { (it.code < 0x20 && it != '\t') || it.code > 0x7e }) return false
        return true
    }

    /** True when [url] has the same scheme, host and port as one of [serverAddresses]. */
    @JvmStatic
    fun isServerOrigin(url: String?, serverAddresses: Collection<String?>): Boolean {
        val target = url?.trim()?.toHttpUrlOrNull() ?: return false
        return isServerOrigin(target, serverAddresses)
    }

    private fun isServerOrigin(target: HttpUrl, serverAddresses: Collection<String?>): Boolean =
        serverAddresses.any { address ->
            val server = address?.trim()?.toHttpUrlOrNull() ?: return@any false
            server.scheme == target.scheme && server.host == target.host && server.port == target.port
        }
}
