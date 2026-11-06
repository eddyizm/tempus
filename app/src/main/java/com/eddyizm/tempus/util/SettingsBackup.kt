package com.eddyizm.tempus.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.XmlResourceParser
import com.eddyizm.tempus.R
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/**
 * Exports and imports the values behind the Settings screen and the theme screen it links to, as
 * JSON. Only those keys are included, so credentials and app state never leave the device.
 */
object SettingsBackup {
    private const val FORMAT = "tempus-settings"
    private const val VERSION = 1

    enum class Type { BOOLEAN, INT, STRING }

    /** The type a key is stored as, and which values of that type the app can read back. */
    class Spec(val type: Type, private val accepts: (Any) -> Boolean = { true }) {
        fun allows(value: Any?): Boolean = when (type) {
            Type.BOOLEAN -> value is Boolean
            Type.INT -> value is Int
            Type.STRING -> value is String
        } && accepts(value!!)
    }

    /** A preference widget as declared in global_preferences.xml. */
    class Declared(
        val tag: String,
        val key: String,
        val entryValues: List<String>? = null,
        val min: Int? = null,
        val max: Int? = null,
    )

    // Values that only make sense on the device or server they were set on.
    private val DEVICE_SPECIFIC = setOf(
        "active_music_folder_id",
        "streaming_cache_storage",
        "download_storage",
    )

    // What the theme screen writes: "DYNAMIC", or a color from its swatches or picker.
    private val COLOR_ACCENT = Regex("DYNAMIC|HEX:#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")

    /** Each exportable key with what it may hold. */
    // The root locale is only used to read the untranslated arrays, not to change the app's language.
    @SuppressLint("AppBundleLocaleChanges")
    @JvmStatic
    fun keys(context: Context): Map<String, Spec> {
        // Some translations carry their own, older copy of a list's values, so accept what this
        // locale's list offers as well as the untranslated values the code compares against.
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.ROOT) }
        val base = context.createConfigurationContext(config).resources
        val values = { id: Int -> (context.resources.getStringArray(id) + base.getStringArray(id)).distinct() }

        val declared = mutableListOf<Declared>()
        context.resources.getXml(R.xml.global_preferences).use { parser ->
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                val key = parser.attr("key") ?: continue
                val entryValues = parser.resourceAttr("entryValues")
                declared += Declared(
                    tag = parser.name,
                    key = key,
                    entryValues = if (entryValues != 0) values(entryValues) else null,
                    min = parser.intAttr("min"),
                    max = parser.intAttr("max"),
                )
            }
        }
        return schema(
            declared,
            themes = values(R.array.theme_list_values),
            languages = UIUtil.getLangPreferenceDropdownEntries(context).values,
        )
    }

    /**
     * Builds the key set from the declared preferences plus the keys that are set elsewhere: the
     * language list, whose values are filled in at runtime, and the theme screen.
     */
    fun schema(declared: List<Declared>, themes: Collection<String>, languages: Collection<String>): Map<String, Spec> {
        val keys = mutableMapOf<String, Spec>()
        for (pref in declared) {
            if (pref.key in DEVICE_SPECIFIC) continue
            keys[pref.key] = when (pref.tag) {
                "SwitchPreference", "CheckBoxPreference" -> Spec(Type.BOOLEAN)
                // SeekBarPreference defaults to 0..100 when min or max is not set.
                "SeekBarPreference" -> (pref.min ?: 0).let { min ->
                    val max = pref.max ?: 100
                    Spec(Type.INT) { it as Int in min..max }
                }
                "EditTextPreference" -> Spec(Type.STRING)
                // A list without values in the XML is filled in at runtime, so it is left out
                // unless it is handled below.
                "ListPreference" -> oneOf(pref.entryValues ?: continue)
                else -> continue
            }
        }
        keys["language"] = oneOf(languages)
        keys[Preferences.THEME] = oneOf(themes)
        keys[Preferences.DARK_THEME_BLACK] = Spec(Type.BOOLEAN)
        keys[Preferences.DYNAMIC_COLOR_ACCENT] = Spec(Type.BOOLEAN)
        keys[Preferences.COLOR_ACCENT] = Spec(Type.STRING) { COLOR_ACCENT.matches(it as String) }
        return keys
    }

    private fun oneOf(values: Collection<String>): Spec {
        val allowed = values.toSet()
        return Spec(Type.STRING) { it in allowed }
    }

    @JvmStatic
    fun exportSettings(prefs: SharedPreferences, keys: Map<String, Spec>): String = toJson(prefs.all, keys)

    /** Returns the number of settings applied, or -1 if the file is not a usable settings export. */
    @JvmStatic
    fun importSettings(json: String, prefs: SharedPreferences, keys: Map<String, Spec>): Int {
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

    fun toJson(values: Map<String, *>, keys: Map<String, Spec>): String {
        val settings = JsonObject()
        for ((key, value) in values.toSortedMap()) {
            val spec = keys[key] ?: continue
            if (!spec.allows(value)) continue
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

    /** Values for [keys] in [json], leaving out any the key's [Spec] does not allow. */
    fun fromJson(json: String, keys: Map<String, Spec>): Map<String, Any>? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val format = root.get("format")?.takeIf { it.isJsonPrimitive }?.asString
        if (format != FORMAT) return null
        // A number with an exact integer value. asInt would accept 1.5 and "1".
        val version = runCatching {
            root.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asBigDecimal?.intValueExact()
        }.getOrNull()
        if (version != VERSION) return null
        val settings = root.get("settings")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null

        val values = mutableMapOf<String, Any>()
        for ((key, element) in settings.entrySet()) {
            val spec = keys[key] ?: continue
            val primitive = element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: continue
            val value: Any = when (spec.type) {
                Type.BOOLEAN -> if (primitive.isBoolean) primitive.asBoolean else continue
                // Whole numbers only. asInt would quietly turn 1.5 into 1.
                Type.INT -> if (primitive.isNumber) {
                    runCatching { primitive.asBigDecimal.intValueExact() }.getOrNull() ?: continue
                } else continue
                Type.STRING -> if (primitive.isString) primitive.asString else continue
            }
            if (spec.allows(value)) values[key] = value
        }
        return values
    }

    private fun XmlResourceParser.attr(name: String): String? =
        NAMESPACES.firstNotNullOfOrNull { getAttributeValue(it, name) }

    private fun XmlResourceParser.resourceAttr(name: String): Int =
        NAMESPACES.map { getAttributeResourceValue(it, name, 0) }.firstOrNull { it != 0 } ?: 0

    private fun XmlResourceParser.intAttr(name: String): Int? =
        NAMESPACES.firstOrNull { getAttributeValue(it, name) != null }?.let { getAttributeIntValue(it, name, 0) }

    private val NAMESPACES = listOf(
        "http://schemas.android.com/apk/res/android",
        "http://schemas.android.com/apk/res-auto",
    )
}
