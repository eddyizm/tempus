package com.eddyizm.tempus.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.XmlResourceParser
import com.eddyizm.tempus.R
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Exports and imports the values behind the Settings screen as JSON. Only keys declared in
 * global_preferences.xml are included, so credentials and app state never leave the device.
 */
object SettingsBackup {
    private const val FORMAT = "tempus-settings"
    private const val VERSION = 1

    // Values that only make sense on the device or server they were set on.
    private val DEVICE_SPECIFIC = setOf(
        "active_music_folder_id",
        "streaming_cache_storage",
        "download_storage",
    )

    @JvmStatic
    fun keys(context: Context): Set<String> {
        val keys = mutableSetOf<String>()
        context.resources.getXml(R.xml.global_preferences).use { parser ->
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                val key = parser.getAttributeValue(ANDROID_NS, "key") ?: parser.getAttributeValue(APP_NS, "key")
                if (key != null) keys.add(key)
            }
        }
        return keys - DEVICE_SPECIFIC
    }

    @JvmStatic
    fun exportSettings(prefs: SharedPreferences, keys: Set<String>): String = toJson(prefs.all, keys)

    /** Returns the number of settings applied, or -1 if the file is not a settings export. */
    @JvmStatic
    fun importSettings(json: String, prefs: SharedPreferences, keys: Set<String>): Int {
        val values = fromJson(json, keys, prefs.all) ?: return -1
        val editor = prefs.edit()
        for ((key, value) in values) {
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.commit()
        return values.size
    }

    fun toJson(values: Map<String, *>, keys: Set<String>): String {
        val settings = JsonObject()
        for ((key, value) in values.toSortedMap()) {
            if (key !in keys) continue
            val entry = JsonObject()
            when (value) {
                is Boolean -> { entry.addProperty("type", "boolean"); entry.addProperty("value", value) }
                is Int -> { entry.addProperty("type", "int"); entry.addProperty("value", value) }
                is Long -> { entry.addProperty("type", "long"); entry.addProperty("value", value) }
                is Float -> { entry.addProperty("type", "float"); entry.addProperty("value", value) }
                is String -> { entry.addProperty("type", "string"); entry.addProperty("value", value) }
                is Set<*> -> {
                    entry.addProperty("type", "string_set")
                    entry.add("value", JsonArray().apply { value.sortedBy { it.toString() }.forEach { add(it.toString()) } })
                }
                else -> continue
            }
            settings.add(key, entry)
        }
        return JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            add("settings", settings)
        }.toString()
    }

    /** Values for [keys] in [json], leaving out any whose type differs from [current]'s. */
    fun fromJson(json: String, keys: Set<String>, current: Map<String, *> = emptyMap<String, Any>()): Map<String, Any>? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        if (root.get("format")?.asString != FORMAT) return null
        val settings = root.getAsJsonObject("settings") ?: return null

        val values = mutableMapOf<String, Any>()
        for ((key, element) in settings.entrySet()) {
            if (key !in keys) continue
            val entry = element as? JsonObject ?: continue
            val value = entry.get("value") ?: continue
            runCatching {
                when (entry.get("type")?.asString) {
                    "boolean" -> value.asBoolean
                    "int" -> value.asInt
                    "long" -> value.asLong
                    "float" -> value.asFloat
                    "string" -> value.asString
                    "string_set" -> value.asJsonArray.map { it.asString }.toSet()
                    else -> null
                }
            }.getOrNull()
                // A value stored with another type would throw ClassCastException where it is read.
                ?.takeIf { value -> current[key].let { it == null || sameType(it, value) } }
                ?.let { values[key] = it }
        }
        return values
    }

    private fun sameType(a: Any, b: Any): Boolean = (a is Set<*> && b is Set<*>) || a.javaClass == b.javaClass

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"
}
