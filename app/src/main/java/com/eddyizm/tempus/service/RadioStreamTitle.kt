package com.eddyizm.tempus.service

// Fields of the "Title~Artist~~Year~~..." format: title, artist, then more fields (year, duration, times, station, id).
private const val TILDE_MIN_FIELDS = 4

/**
 * Splits the song text a radio station sends (ICY StreamTitle) into (artist, title).
 * Known formats: "Artist - Title" and "Title~Artist~~Year~~..." (Virgin Radio).
 * Anything else is returned as a title with no artist.
 */
internal fun parseStreamTitle(raw: String): Pair<String?, String?> {
    val text = raw.trim()
    if (text.isEmpty()) return null to null

    val fields = text.split("~")
    if (fields.size >= TILDE_MIN_FIELDS && fields[0].isNotBlank() && fields[1].isNotBlank()) {
        return fields[1].trim() to fields[0].trim()
    }

    val parts = text.split(" - ", limit = 2)
    return if (parts.size == 2) {
        parts[0].trim().ifEmpty { null } to parts[1].trim().ifEmpty { null }
    } else {
        null to text
    }
}
