package com.eddyizm.tempus.service

import android.util.Log
import com.eddyizm.tempus.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

private const val TAG = "RadioTrackCoverResolver"
private const val MAX_ARCHIVE_ATTEMPTS = 5
private const val CACHE_SIZE = 50
private const val MUSICBRAINZ_DELAY_MS = 1100L
private const val TIMEOUT_MS = 8000
private val USER_AGENT = "Tempus/${BuildConfig.VERSION_NAME} (https://github.com/eddyizm/tempus)"

private val titleNoise = Regex(
    """\s*[(\[][^)\]]*(edit|remaster|mix|version|live|mono|stereo)[^)\]]*[)\]]""",
    RegexOption.IGNORE_CASE
)

internal fun cleanTitle(raw: String): String =
    raw.replace(titleNoise, "").trim().ifEmpty { raw }

internal fun luceneEscape(s: String): String = s.replace("\\", " ").replace("\"", " ")

private val accents = Regex("\\p{M}+")
private val leadingThe = Regex("^the\\s+")
private val artistSeparator = Regex("""\s*(,|&|;|\+|\[\+]|\band\b|\bfeat\.?|\bft\.?|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)

// Compares text ignoring case, accents, punctuation and typographic apostrophes.
internal fun normalizeText(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(accents, "").lowercase().replace("&", "and").filter { it.isLetterOrDigit() }

// "The Beatles" and "BEATLES" are the same artist for us.
internal fun normalizeArtist(s: String): String =
    normalizeText(Normalizer.normalize(s, Normalizer.Form.NFD).replace(accents, "").lowercase().trim().replace(leadingThe, ""))

// "Eric Prydz, Hans Zimmer" -> "Eric Prydz". "AC/DC" has no separator and stays whole.
internal fun primaryArtist(s: String): String = s.split(artistSeparator, limit = 2).first().trim().ifEmpty { s.trim() }

private fun wantedArtists(artist: String) = setOf(normalizeArtist(artist), normalizeArtist(primaryArtist(artist)))

// A hit from Deezer or iTunes is accepted only for the same artist and the same song.
private fun isSameSong(foundArtist: String, foundTitle: String, artist: String, title: String): Boolean {
    if (normalizeArtist(foundArtist) !in wantedArtists(artist)) return false
    val found = normalizeText(foundTitle)
    val wanted = normalizeText(title)
    return wanted.isNotEmpty() && found.isNotEmpty() && (found.contains(wanted) || wanted.contains(found))
}

private const val MIN_MUSICBRAINZ_SCORE = 90

// The search is a phrase match ("Eagles" also finds "Eagles of Death Metal"), so keep only
// recordings whose title and artist credit are the ones asked for (see normalizeText, primaryArtist).
private fun JSONObject.isRecordingOf(artist: String, title: String): Boolean {
    if (optInt("score") < MIN_MUSICBRAINZ_SCORE || normalizeText(optString("title")) != normalizeText(title)) return false
    val credits = optJSONArray("artist-credit") ?: return false
    val wanted = wantedArtists(artist)
    return (0 until credits.length()).any { i ->
        val credit = credits.getJSONObject(i)
        normalizeArtist(credit.optString("name")) in wanted ||
            normalizeArtist(credit.optJSONObject("artist")?.optString("name").orEmpty()) in wanted
    }
}

// The studio recording is the one on the most clean releases (official, no live/compilation/demo).
// Release order: Album, then Single, then the rest.
internal fun musicBrainzReleaseIds(json: String, artist: String, title: String): List<String> {
    val recordings = JSONObject(json).optJSONArray("recordings") ?: return emptyList()
    var best = emptyList<Pair<String, String>>() // (primary type, release id)
    for (i in 0 until recordings.length()) {
        val recording = recordings.getJSONObject(i)
        if (!recording.isRecordingOf(artist, title)) continue
        val releases = recording.optJSONArray("releases") ?: continue
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

// The lookup runs in the background: the user may have started another station or track meanwhile.
internal fun isSameRadioSong(
    currentMediaId: String?,
    startedMediaId: String,
    isRadio: Boolean,
    radioArtist: String?,
    radioTitle: String?,
    artist: String,
    title: String,
): Boolean = currentMediaId == startedMediaId && isRadio && radioArtist == artist && radioTitle == title

internal fun deezerCover(json: String, artist: String, title: String): String? {
    val data = JSONObject(json).optJSONArray("data") ?: return null
    for (i in 0 until data.length()) {
        val track = data.getJSONObject(i)
        if (isSameSong(track.getJSONObject("artist").optString("name"), track.optString("title"), artist, title)) {
            val url = track.getJSONObject("album").optString("cover_xl")
            if (url.isNotEmpty()) return url
        }
    }
    return null
}

internal fun itunesCover(json: String, artist: String, title: String): String? {
    val results = JSONObject(json).optJSONArray("results") ?: return null
    for (i in 0 until results.length()) {
        val track = results.getJSONObject(i)
        if (isSameSong(track.optString("artistName"), track.optString("trackName"), artist, title)) {
            val url = track.optString("artworkUrl100").replace("100x100bb", "600x600bb")
            if (url.isNotEmpty()) return url
        }
    }
    return null
}

/**
 * Finds the cover of a radio song: MusicBrainz + Cover Art Archive, then Deezer, then iTunes.
 * Blocking, call it off the main thread. Never throws. [get] returns the body or null,
 * [exists] tells if a URL answers 200.
 */
class RadioTrackCoverResolver(
    private val get: (String) -> String? = ::httpGet,
    private val exists: (String) -> Boolean = ::httpHead,
) {
    // "" means "looked up, not found". A lookup that hit a network error is not cached.
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > CACHE_SIZE
    }

    fun resolve(artist: String, title: String): String? {
        val key = "${artist.lowercase()}|${title.lowercase()}"
        synchronized(cache) { cache[key] }?.let { return it.ifEmpty { null } }

        var failed = false
        fun <T> guarded(source: String, block: () -> T?): T? = try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$source failed", e)
            failed = true
            null
        }

        val cleaned = cleanTitle(title)
        val url = guarded("MusicBrainz") { fromMusicBrainz(artist, cleaned) }
            ?: guarded("Deezer") { fromDeezer(artist, cleaned) }
            ?: guarded("iTunes") { fromItunes(artist, cleaned) }
        if (url != null || !failed) synchronized(cache) { cache[key] = url ?: "" }
        return url
    }

    private fun body(url: String): String = get(url) ?: throw IOException("No answer from $url")

    private fun fromMusicBrainz(artist: String, title: String): String? {
        val query = "recording:\"${luceneEscape(title.replace('\u2019', '\''))}\" AND artist:\"${luceneEscape(primaryArtist(artist))}\"" +
            " AND status:official AND NOT comment:live"
        val ids = musicBrainzReleaseIds(body("https://musicbrainz.org/ws/2/recording/?fmt=json&limit=25&query=${enc(query)}"), artist, title)
        return ids.take(MAX_ARCHIVE_ATTEMPTS)
            .map { "https://coverartarchive.org/release/$it/front-500" }
            .firstOrNull { exists(it) }
    }

    private fun fromDeezer(artist: String, title: String): String? =
        deezerCover(body("https://api.deezer.com/search?limit=5&q=${enc("${primaryArtist(artist)} $title")}"), artist, title)

    private fun fromItunes(artist: String, title: String): String? =
        itunesCover(body("https://itunes.apple.com/search?entity=song&limit=5&term=${enc("${primaryArtist(artist)} $title")}"), artist, title)

    companion object {
        val instance = RadioTrackCoverResolver()
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

private fun open(url: String, method: String): HttpURLConnection {
    // MusicBrainz allows about 1 request per second
    if ("musicbrainz.org" in url) Thread.sleep(MUSICBRAINZ_DELAY_MS)
    return (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("User-Agent", USER_AGENT)
    }
}

internal fun httpGet(url: String): String? = try {
    val connection = open(url, "GET")
    try {
        if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText() else null
    } finally {
        connection.disconnect()
    }
} catch (e: IOException) {
    null
}

internal fun httpHead(url: String): Boolean = try {
    val connection = open(url, "HEAD")
    try {
        connection.responseCode == 200
    } finally {
        connection.disconnect()
    }
} catch (e: IOException) {
    false
}
