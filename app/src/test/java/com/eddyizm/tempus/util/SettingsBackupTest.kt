package com.eddyizm.tempus.util

import com.eddyizm.tempus.util.SettingsBackup.Declared
import com.eddyizm.tempus.util.SettingsBackup.Spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SettingsBackupTest {

    // Built from the app's own preference XML and arrays, the same way SettingsBackup.keys does
    // on a device, so these tests cover what a real export contains.
    private val keys: Map<String, Spec> by lazy { realKeys() }

    private val stored = mapOf(
        "theme" to "dark",
        "dark_theme_black" to true,
        "dynamic_color_accent" to false,
        "color_accent" to "HEX:#FF1976D2",
        "language" to "de-DE",
        "wifi_only" to true,
        "tile_size" to "3",
        "selected_equalizer" to "1",
        "loudness_preamp" to -3,
        "number_tracks_keep_in_queue" to 40,
        "network_ping_timeout_base" to "5",
        "password" to "secret",
        "token" to "abc",
    )

    private fun settings(body: String) = """{"format":"tempus-settings","version":1,"settings":{$body}}"""

    @Test
    fun exportsTheThemeScreen() {
        val json = SettingsBackup.toJson(stored, keys)
        val imported = SettingsBackup.fromJson(json, keys)!!
        assertEquals("dark", imported["theme"])
        assertEquals(true, imported["dark_theme_black"])
        assertEquals(false, imported["dynamic_color_accent"])
        assertEquals("HEX:#FF1976D2", imported["color_accent"])
    }

    @Test
    fun keysCoverTheSettingsScreen() {
        for (key in listOf("language", "selected_equalizer", "replay_gain_mode", "loudness_preamp",
                "wifi_only", "download_wifi_only", "lan_remote_enabled", "network_ping_timeout_base")) {
            assertTrue(key, key in keys)
        }
        // Every list on the screen gets its values from the XML, except the ones filled in at runtime.
        val lists = declared().filter { it.tag == "ListPreference" }.map { it.key }
        assertEquals(setOf("active_music_folder_id"), lists.toSet() - keys.keys)
    }

    @Test
    fun leavesOutDeviceSpecificAndNonSettingKeys() {
        for (key in listOf("active_music_folder_id", "streaming_cache_storage", "download_storage",
                "settings_export", "password", "token")) {
            assertFalse(key, key in keys)
        }
        val json = SettingsBackup.toJson(stored, keys)
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("token"))
    }

    @Test
    fun roundTripsEveryType() {
        val json = SettingsBackup.toJson(stored, keys)
        assertEquals(stored - "password" - "token", SettingsBackup.fromJson(json, keys))
    }

    @Test
    fun importSkipsListValuesTheListDoesNotOffer() {
        val json = settings("""
            "selected_equalizer":"x",
            "theme":"purple",
            "replay_gain_mode":"loud",
            "language":"xx-bogus",
            "color_accent":"HEX:nope",
            "tile_size":"3"""")
        assertEquals(mapOf("tile_size" to "3"), SettingsBackup.fromJson(json, keys))
    }

    @Test
    fun importAcceptsEveryValueAListOffers() {
        for (value in listOf("0", "1", "2", "3")) {
            val json = settings(""""selected_equalizer":"$value"""")
            assertEquals(mapOf("selected_equalizer" to value), SettingsBackup.fromJson(json, keys))
        }
        for (value in listOf("DYNAMIC", "HEX:#2E7D32", "HEX:#FFB5076B")) {
            val json = settings(""""color_accent":"$value"""")
            assertEquals(mapOf("color_accent" to value), SettingsBackup.fromJson(json, keys))
        }
    }

    @Test
    fun importSkipsNumbersOutsideTheSeekBarRange() {
        val json = settings("""
            "loudness_preamp":100,
            "number_tracks_keep_in_queue":5,
            "min_star_rating":1.5,
            "replay_gain_prevent_clipping":true""")
        assertEquals(mapOf("replay_gain_prevent_clipping" to true), SettingsBackup.fromJson(json, keys))
        // The parser is lenient and lets NaN through.
        assertEquals(emptyMap<String, Any>(), SettingsBackup.fromJson(settings(""""loudness_preamp":NaN"""), keys))
        assertEquals(mapOf("loudness_preamp" to -15),
            SettingsBackup.fromJson(settings(""""loudness_preamp":-15"""), keys))
    }

    @Test
    fun importSkipsValuesOfTheWrongType() {
        val json = settings("""
            "theme":1,
            "wifi_only":"yes",
            "tile_size":"3",
            "number_tracks_keep_in_queue":"40"""")
        assertEquals(mapOf("tile_size" to "3"), SettingsBackup.fromJson(json, keys))
    }

    @Test
    fun exportSkipsStoredValuesTheKeyDoesNotAllow() {
        val json = SettingsBackup.toJson(mapOf("theme" to 1, "selected_equalizer" to "x", "wifi_only" to true), keys)
        assertEquals(mapOf("wifi_only" to true), SettingsBackup.fromJson(json, keys))
    }

    @Test
    fun rejectsOtherFilesAndVersions() {
        assertNull(SettingsBackup.fromJson("", keys))
        assertNull(SettingsBackup.fromJson("not json", keys))
        assertNull(SettingsBackup.fromJson("[1,2]", keys))
        assertNull(SettingsBackup.fromJson("""{"settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":null,"settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":"invalid","settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":2,"settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":1.5,"settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":"1","settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":1}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":1,"settings":[]}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":1,"settings":null}""", keys))
    }

    @Test
    fun skipsMalformedEntries() {
        val json = settings("""
            "theme":"dark",
            "wifi_only":{"value":true},
            "tile_size":["3"],
            "number_tracks_keep_in_queue":null""")
        assertEquals(mapOf("theme" to "dark"), SettingsBackup.fromJson(json, keys))
    }

    private val res = File("src/main/res")

    private fun parse(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(res, path)).documentElement

    private fun Element.attr(name: String): String? =
        (0 until attributes.length).map { attributes.item(it) }
            .firstOrNull { (it.localName ?: it.nodeName) == name }?.nodeValue

    private fun Element.descendants(): List<Element> {
        val nodes = getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val arrays: Map<String, List<String>> by lazy {
        parse("values/arrays.xml").descendants().filter { it.tagName == "string-array" }.associate { array ->
            array.attr("name")!! to array.descendants().map { it.textContent.trim() }
        }
    }

    private fun declared(): List<Declared> = parse("xml/global_preferences.xml").descendants().mapNotNull { el ->
        val key = el.attr("key") ?: return@mapNotNull null
        Declared(
            tag = el.tagName,
            key = key,
            entryValues = el.attr("entryValues")?.removePrefix("@array/")?.let { arrays.getValue(it) },
            min = el.attr("min")?.toInt(),
            max = el.attr("max")?.toInt(),
        )
    }

    private fun realKeys(): Map<String, Spec> {
        val languages = parse("xml/locale_config.xml").descendants().map { it.attr("name")!! } + "default"
        return SettingsBackup.schema(declared(), arrays.getValue("theme_list_values"), languages)
    }
}
