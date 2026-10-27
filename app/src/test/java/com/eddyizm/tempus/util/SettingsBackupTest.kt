package com.eddyizm.tempus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupTest {

    private val keys = setOf("theme", "wifi_only", "tile_size", "image_cache_size", "loudness_preamp", "sections")

    private val stored = mapOf(
        "theme" to "dark",
        "wifi_only" to true,
        "tile_size" to 3,
        "image_cache_size" to 500L,
        "loudness_preamp" to 1.5f,
        "sections" to setOf("b", "a"),
        "password" to "secret",
        "token" to "abc",
    )

    @Test
    fun roundTripsEveryType() {
        val json = SettingsBackup.toJson(stored, keys)
        assertEquals(stored - "password" - "token", SettingsBackup.fromJson(json, keys))
    }

    @Test
    fun leavesOutKeysNotInTheSettingsScreen() {
        val json = SettingsBackup.toJson(stored, keys)
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("token"))
    }

    @Test
    fun importIgnoresUnknownKeys() {
        val json = SettingsBackup.toJson(stored, keys + "password")
        assertEquals(keys, SettingsBackup.fromJson(json, keys)!!.keys)
    }

    @Test
    fun importSkipsValuesStoredWithAnotherType() {
        val json = SettingsBackup.toJson(stored, keys)
        val values = SettingsBackup.fromJson(json, keys, mapOf("tile_size" to "3", "theme" to "light"))!!
        assertFalse(values.containsKey("tile_size"))
        assertEquals("dark", values["theme"])
    }

    @Test
    fun rejectsOtherFiles() {
        assertNull(SettingsBackup.fromJson("", keys))
        assertNull(SettingsBackup.fromJson("not json", keys))
        assertNull(SettingsBackup.fromJson("""{"settings":{}}""", keys))
        assertNull(SettingsBackup.fromJson("[1,2]", keys))
    }

    @Test
    fun skipsMalformedEntries() {
        val json = """{"format":"tempus-settings","version":1,"settings":{
            "theme":{"type":"string","value":"dark"},
            "tile_size":{"type":"int","value":"big"},
            "wifi_only":{"type":"unknown","value":true},
            "sections":"oops"}}"""
        assertEquals(mapOf("theme" to "dark"), SettingsBackup.fromJson(json, keys))
    }
}
