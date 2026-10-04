package com.eddyizm.tempus.subsonic.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

public class BookmarksDeserializerTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Bookmarks.class, new BookmarksDeserializer())
            .create();

    @Test
    public void parsesArrayOfBookmarks() {
        String json = "{\"bookmark\":[{\"position\":1000,\"entry\":{\"id\":\"1\",\"title\":\"A\"}},{\"position\":2000,\"entry\":{\"id\":\"2\",\"title\":\"B\"}}]}";
        Bookmarks result = gson.fromJson(json, Bookmarks.class);

        assertNotNull(result.getBookmarks());
        assertEquals(2, result.getBookmarks().size());
        assertEquals(1000L, result.getBookmarks().get(0).getPosition());
        assertEquals("1", result.getBookmarks().get(0).getEntry().getId());
        assertEquals(2000L, result.getBookmarks().get(1).getPosition());
    }

    @Test
    public void parsesSingleBookmarkObject() {
        // Navidrome returns a single bookmark as an object, not an array (navidrome#1099).
        String json = "{\"bookmark\":{\"position\":5000,\"entry\":{\"id\":\"9\",\"title\":\"C\"}}}";
        Bookmarks result = gson.fromJson(json, Bookmarks.class);

        assertNotNull(result.getBookmarks());
        assertEquals(1, result.getBookmarks().size());
        assertEquals(5000L, result.getBookmarks().get(0).getPosition());
        assertEquals("9", result.getBookmarks().get(0).getEntry().getId());
    }

    @Test
    public void missingBookmarkYieldsNull() {
        Bookmarks result = gson.fromJson("{}", Bookmarks.class);
        assertNull(result.getBookmarks());
    }
}
