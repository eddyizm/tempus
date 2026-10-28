package com.eddyizm.tempus.util

import com.eddyizm.tempus.util.SettingsBackup.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackupTest {

    private val keys = mapOf(
        "theme" to Type.STRING,
        "wifi_only" to Type.BOOLEAN,
        "tile_size" to Type.STRING,
        "number_tracks_keep_in_queue" to Type.INT,
    )

    private val stored = mapOf(
        "theme" to "dark",
        "wifi_only" to true,
        "tile_size" to "3",
        "number_tracks_keep_in_queue" to 40,
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
        val json = SettingsBackup.toJson(stored, keys + ("password" to Type.STRING))
        assertEquals(keys.keys, SettingsBackup.fromJson(json, keys)!!.keys)
    }

    @Test
    fun importSkipsValuesOfTheWrongType() {
        val json = """{"format":"tempus-settings","version":1,"settings":{
            "theme":1,
            "wifi_only":"yes",
            "tile_size":"3",
            "number_tracks_keep_in_queue":"40"}}"""
        assertEquals(mapOf("tile_size" to "3"), SettingsBackup.fromJson(json, keys))
    }

    @Test
    fun exportSkipsStoredValuesOfTheWrongType() {
        val json = SettingsBackup.toJson(mapOf("theme" to 1, "wifi_only" to true), keys)
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
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":1}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":1,"settings":[]}""", keys))
        assertNull(SettingsBackup.fromJson("""{"format":"tempus-settings","version":1,"settings":null}""", keys))
    }

    @Test
    fun skipsMalformedEntries() {
        val json = """{"format":"tempus-settings","version":1,"settings":{
            "theme":"dark",
            "wifi_only":{"value":true},
            "tile_size":["3"],
            "number_tracks_keep_in_queue":null}}"""
        assertEquals(mapOf("theme" to "dark"), SettingsBackup.fromJson(json, keys))
    }
}
