package com.eddyizm.tempus.service

private const val TILDE_MIN_FIELDS = 4

private const val MIN_SLOGAN_LENGTH = 4

private val dashSeparator = Regex("""\s+[-\u2013\u2014]\s+""")

private val trailingYear = Regex("""(19|20)\d{2}""")

private fun normalized(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

private fun isStationName(part: String, stationName: String?): Boolean {
    val name = normalized(part)
    return name.length >= MIN_SLOGAN_LENGTH && normalized(stationName.orEmpty()).contains(name)
}

/**
 * Splits the song text of a radio stream into (artist, title), for the cover lookup only.
 * Formats: "Artist - Title", "Artist - Title - Year", "Title - Artist - Title" and "Title~Artist~~Year~~...".
 * A first part contained in the station name is a slogan and is dropped. No artist means no lookup.
 */
internal fun parseStreamTitle(raw: String, stationName: String? = null): Pair<String?, String?> {
    val text = raw.trim()
    if (text.none { it.isLetterOrDigit() }) return null to null

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
