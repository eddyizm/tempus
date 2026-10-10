package com.eddyizm.tempus.subsonic.models

import androidx.annotation.Keep
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

@Keep
class Bookmarks {
    @SerializedName("bookmark")
    var bookmarks: List<Bookmark>? = null
}

/**
 * Navidrome returns a single bookmark as an object instead of an array
 * (navidrome/navidrome#1099); tolerate both shapes.
 */
class BookmarksDeserializer : JsonDeserializer<Bookmarks> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Bookmarks {
        val result = Bookmarks()
        val bookmark = json.asJsonObject.get("bookmark") ?: return result
        result.bookmarks = when {
            bookmark.isJsonArray -> context.deserialize(bookmark, object : TypeToken<List<Bookmark>>() {}.type)
            bookmark.isJsonObject -> listOf(context.deserialize(bookmark, Bookmark::class.java))
            else -> null
        }
        return result
    }
}
