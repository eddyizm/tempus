package com.eddyizm.tempus.service

// Fields of the "Title~Artist~~Year~~..." format: title, artist, then more fields (year, duration, times, station, id).
private const val TILDE_MIN_FIELDS = 4

// A station name shorter than this inside the station's own name is too weak to call a slogan ("U2" in "U2 Radio").
private const val MIN_SLOGAN_LENGTH = 4

private val dashSeparator = Regex("""\s+[-\u2013\u2014]\s+""")

// "Artist - Title - 1978": the year is not part of the title. With two parts "Prince - 1999" is a title.
private val trailingYear = Regex("""(19|20)\d{2}""")

private fun normalized(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

// Stations often send their own name or slogan as the first part ("Virgin Radio - Style Rock").
private fun isStationName(part: String, stationName: String?): Boolean {
    val name = normalized(part)
    return name.length >= MIN_SLOGAN_LENGTH && normalized(stationName.orEmpty()).contains(name)
}

/**
 * Splits the song text a radio station sends (ICY StreamTitle) into (artist, title).
 * Known formats:
 * - "Artist - Title" (hyphen, en dash or em dash), also "Title - Artist - Title"
 * - "Artist - Title - Year": the year is dropped
 * - "Title~Artist~~Year~~..." (Virgin Radio)
 * A first part equal to the station name is dropped. Anything else is a title with no artist.
 */
internal fun parseStreamTitle(raw: String, stationName: String? = null): Pair<String?, String?> {
    val text = raw.trim()
    if (text.isEmpty()) return null to null

    val fields = text.split("~")
    if (fields.size >= TILDE_MIN_FIELDS && fields[0].isNotBlank() && fields[1].isNotBlank()) {
        return fields[1].trim() to fields[0].trim()
    }

    var parts = text.split(dashSeparator).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null to text
    if (parts.size > 1 && isStationName(parts[0], stationName)) parts = parts.drop(1)
    if (parts.size >= 3 && trailingYear.matches(parts.last())) parts = parts.dropLast(1)

    return when {
        parts.size == 1 -> null to parts[0]
        parts.size >= 3 && parts.first().equals(parts.last(), ignoreCase = true) -> parts[1] to parts.first()
        else -> parts[0] to parts.drop(1).joinToString(" - ")
    }
}
