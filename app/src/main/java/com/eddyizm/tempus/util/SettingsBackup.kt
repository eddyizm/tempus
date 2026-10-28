package com.eddyizm.tempus.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.XmlResourceParser
import com.eddyizm.tempus.R
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Exports and imports the values behind the Settings screen as JSON. Only keys declared in
 * global_preferences.xml are included, so credentials and app state never leave the device.
 */
object SettingsBackup {
    private const val FORMAT = "tempus-settings"
    private const val VERSION = 1

    enum class Type { BOOLEAN, INT, STRING }

    // Values that only make sense on the device or server they were set on.
    private val DEVICE_SPECIFIC = setOf(
        "active_music_folder_id",
        "streaming_cache_storage",
        "download_storage",
    )

    /** Each exportable key with the type its preference widget stores. */
    @JvmStatic
    fun keys(context: Context): Map<String, Type> {
        val keys = mutableMapOf<String, Type>()
        context.resources.getXml(R.xml.global_preferences).use { parser ->
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                val type = when (parser.name) {
                    "SwitchPreference", "CheckBoxPreference" -> Type.BOOLEAN
                    "SeekBarPreference" -> Type.INT
                    "ListPreference", "EditTextPreference" -> Type.STRING
                    else -> continue
                }
                val key = parser.getAttributeValue(ANDROID_NS, "key") ?: parser.getAttributeValue(APP_NS, "key")
                if (key != null && key !in DEVICE_SPECIFIC) keys[key] = type
            }
        }
        return keys
    }

    @JvmStatic
    fun exportSettings(prefs: SharedPreferences, keys: Map<String, Type>): String = toJson(prefs.all, keys)

    /** Returns the number of settings applied, or -1 if the file is not a usable settings export. */
    @JvmStatic
    fun importSettings(json: String, prefs: SharedPreferences, keys: Map<String, Type>): Int {
        val values = fromJson(json, keys) ?: return -1
        val editor = prefs.edit()
        for ((key, value) in values) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
            }
        }
        return if (editor.commit()) values.size else -1
    }

    fun toJson(values: Map<String, *>, keys: Map<String, Type>): String {
        val settings = JsonObject()
        for ((key, value) in values.toSortedMap()) {
            val expected = keys[key] ?: continue
            if (!matches(value, expected)) continue
            when (value) {
                is Boolean -> settings.addProperty(key, value)
                is Number -> settings.addProperty(key, value)
                is String -> settings.addProperty(key, value)
            }
        }
        return JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            add("settings", settings)
        }.toString()
    }

    /** Values for [keys] in [json], leaving out any whose JSON type differs from the key's type. */
    fun fromJson(json: String, keys: Map<String, Type>): Map<String, Any>? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val format = root.get("format")?.takeIf { it.isJsonPrimitive }?.asString
        if (format != FORMAT) return null
        val version = runCatching { root.get("version")?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
        if (version != VERSION) return null
        val settings = root.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null

        val values = mutableMapOf<String, Any>()
        for ((key, element) in settings.entrySet()) {
            val expected = keys[key] ?: continue
            val primitive = element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: continue
            val value: Any = when (expected) {
                Type.BOOLEAN -> if (primitive.isBoolean) primitive.asBoolean else continue
                Type.INT -> if (primitive.isNumber) primitive.asInt else continue
                Type.STRING -> if (primitive.isString) primitive.asString else continue
            }
            values[key] = value
        }
        return values
    }

    private fun matches(value: Any?, type: Type) = when (type) {
        Type.BOOLEAN -> value is Boolean
        Type.INT -> value is Int
        Type.STRING -> value is String
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"
}
