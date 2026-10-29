package com.eddyizm.tempus.subsonic.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

// This adapter handles Date objects, returning null if the JSON string is empty or unparsable.
// Servers send ISO-8601 with 0-9 fraction digits (Navidrome uses RFC3339Nano) and optional offsets.
class EmptyDateTypeAdapter : JsonDeserializer<Date> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Date? {
        if (json.isJsonNull) return null

        if (json.isJsonPrimitive && json.asJsonPrimitive.isNumber) {
            return Date(json.asLong)
        }

        return parse(json.asString)
    }

    companion object {
        private val ISO_8601 = Regex(
            "^(\\d{4})-(\\d{2})-(\\d{2})" +
                "(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:[.,](\\d+))?)?)?" +
                "\\s*(Z|z|[+-]\\d{2}(?::?\\d{2})?)?$"
        )

        @JvmStatic
        fun parse(value: String?): Date? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null

            val match = ISO_8601.matchEntire(text) ?: return null
            val g = match.groupValues

            return try {
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    isLenient = false
                    set(
                        g[1].toInt(),
                        g[2].toInt() - 1,
                        g[3].toInt(),
                        g[4].ifEmpty { "0" }.toInt(),
                        g[5].ifEmpty { "0" }.toInt(),
                        g[6].ifEmpty { "0" }.toInt()
                    )
                    set(Calendar.MILLISECOND, g[7].padEnd(3, '0').take(3).toInt())
                }

                var millis = calendar.timeInMillis
                millis -= offsetMillis(g[8])
                Date(millis)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        private fun offsetMillis(zone: String): Long {
            if (zone.isEmpty() || zone.equals("Z", ignoreCase = true)) return 0L

            val sign = if (zone[0] == '-') -1 else 1
            val digits = zone.substring(1).replace(":", "")
            val hours = digits.take(2).toInt()
            val minutes = if (digits.length >= 4) digits.substring(2, 4).toInt() else 0
            return sign * (hours * 3_600_000L + minutes * 60_000L)
        }
    }
}