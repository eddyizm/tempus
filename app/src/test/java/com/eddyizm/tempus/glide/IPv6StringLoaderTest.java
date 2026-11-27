package com.eddyizm.tempus.glide;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IPv6StringLoaderTest {

    @Test
    public void capitalisedSchemeIsStillAnHttpUrl() {
        assertTrue(IPv6StringLoader.isHttpUrl("Https://music.ortolan.us/rest/getCoverArt?id=1"));
        assertTrue(IPv6StringLoader.isHttpUrl("HTTP://192.168.3.11:4533/rest/getCoverArt"));
    }

    @Test
    public void normalSchemesAreHandled() {
        assertTrue(IPv6StringLoader.isHttpUrl("https://music.ortolan.us/rest/getCoverArt"));
        assertTrue(IPv6StringLoader.isHttpUrl("http://192.168.3.11:4533/rest/getCoverArt"));
    }

    @Test
    public void otherModelsAreRejected() {
        assertFalse(IPv6StringLoader.isHttpUrl("content://media/1"));
        assertFalse(IPv6StringLoader.isHttpUrl("/sdcard/cover.jpg"));
        assertFalse(IPv6StringLoader.isHttpUrl("ftp://example.com/cover.jpg"));
    }
}
