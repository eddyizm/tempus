package com.eddyizm.tempus.service

import org.json.JSONObject

private val titleNoise = Regex(
    """\s*[(\[][^)\]]*(edit|remaster|mix|version|live|mono|stereo)[^)\]]*[)\]]""",
    RegexOption.IGNORE_CASE
)

internal fun cleanTitle(raw: String): String =
    raw.replace(titleNoise, "").trim().ifEmpty { raw }

internal fun luceneEscape(s: String): String = s.replace("\\", " ").replace("\"", " ")

// The studio recording is the one on the most clean releases (official, no live/compilation/demo).
// Release order: Album, then Single, then the rest.
internal fun musicBrainzReleaseIds(json: String): List<String> {
    val recordings = JSONObject(json).optJSONArray("recordings") ?: return emptyList()
    var best = emptyList<Pair<String, String>>() // (primary type, release id)
    for (i in 0 until recordings.length()) {
        val releases = recordings.getJSONObject(i).optJSONArray("releases") ?: continue
        val clean = (0 until releases.length()).mapNotNull { j ->
            val release = releases.getJSONObject(j)
            val group = release.optJSONObject("release-group") ?: return@mapNotNull null
            if (release.optString("status") != "Official" || (group.optJSONArray("secondary-types")?.length() ?: 0) > 0) {
                return@mapNotNull null
            }
            group.optString("primary-type") to release.getString("id")
        }
        if (clean.size > best.size) best = clean
    }
    return best.sortedBy { (type, _) -> when (type) { "Album" -> 0; "Single" -> 1; else -> 2 } }.map { it.second }
}

internal fun deezerCover(json: String, artist: String): String? {
    val data = JSONObject(json).optJSONArray("data") ?: return null
    for (i in 0 until data.length()) {
        val track = data.getJSONObject(i)
        if (track.getJSONObject("artist").optString("name").equals(artist, ignoreCase = true)) {
            val url = track.getJSONObject("album").optString("cover_xl")
            if (url.isNotEmpty()) return url
        }
    }
    return null
}

internal fun itunesCover(json: String, artist: String): String? {
    val results = JSONObject(json).optJSONArray("results") ?: return null
    for (i in 0 until results.length()) {
        val track = results.getJSONObject(i)
        if (track.optString("artistName").equals(artist, ignoreCase = true)) {
            val url = track.optString("artworkUrl100").replace("100x100bb", "600x600bb")
            if (url.isNotEmpty()) return url
        }
    }
    return null
}
