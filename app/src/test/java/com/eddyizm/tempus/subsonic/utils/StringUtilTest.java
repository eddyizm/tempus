package com.eddyizm.tempus.subsonic.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringUtilTest {

    @Test
    public void capitalisedSchemeIsLowercased() {
        assertEquals("https://music.ortolan.us",
                StringUtil.normalizeScheme("Https://music.ortolan.us"));
    }

    @Test
    public void mixedCaseSchemeIsLowercasedAndAddressTrimmed() {
        assertEquals("http://192.168.3.11:4533",
                StringUtil.normalizeScheme("  HtTp://192.168.3.11:4533  "));
    }

    @Test
    public void anAddressWithoutASchemeIsLeftAlone() {
        assertEquals("music.ortolan.us", StringUtil.normalizeScheme("music.ortolan.us"));
    }

    @Test
    public void aNullAddressStaysNull() {
        assertNull(StringUtil.normalizeScheme(null));
    }

    @Test
    public void capitalisedSchemeIsStillAnHttpUrl() {
        assertTrue(StringUtil.isHttpUrl("Https://music.ortolan.us/rest/getCoverArt?id=1"));
        assertTrue(StringUtil.isHttpUrl("HTTP://192.168.3.11:4533/rest/getCoverArt"));
    }

    @Test
    public void normalSchemesAreHandled() {
        assertTrue(StringUtil.isHttpUrl("https://music.ortolan.us/rest/getCoverArt"));
        assertTrue(StringUtil.isHttpUrl("http://192.168.3.11:4533/rest/getCoverArt"));
    }

    @Test
    public void otherModelsAreRejected() {
        assertFalse(StringUtil.isHttpUrl("content://media/1"));
        assertFalse(StringUtil.isHttpUrl("/sdcard/cover.jpg"));
        assertFalse(StringUtil.isHttpUrl("ftp://example.com/cover.jpg"));
        assertFalse(StringUtil.isHttpUrl(null));
    }
}
