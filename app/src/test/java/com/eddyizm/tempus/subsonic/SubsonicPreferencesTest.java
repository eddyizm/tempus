package com.eddyizm.tempus.subsonic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SubsonicPreferencesTest {

    @Test
    public void capitalisedSchemeIsLowercased() {
        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl("Https://music.ortolan.us");

        assertEquals("https://music.ortolan.us", preferences.getServerUrl());
    }

    @Test
    public void mixedCaseSchemeIsLowercasedAndAddressTrimmed() {
        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl("  HtTp://192.168.3.11:4533  ");

        assertEquals("http://192.168.3.11:4533", preferences.getServerUrl());
    }

    @Test
    public void anAddressWithoutASchemeIsLeftAlone() {
        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl("music.ortolan.us");

        assertEquals("music.ortolan.us", preferences.getServerUrl());
    }

    @Test
    public void aNullAddressStaysNull() {
        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(null);

        assertNull(preferences.getServerUrl());
    }
}
