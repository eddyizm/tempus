package com.eddyizm.tempus.glide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class IPv6StringLoaderTest {

    private static final String BASE = "https://music.example.com/rest/getCoverArt";

    @Test
    public void aNewSaltKeepsTheSameKey() {
        String first = BASE + "?u=me&s=salt1&t=token1&v=1.16.1&c=Tempus&size=500&id=al-1";
        String second = BASE + "?u=me&s=salt2&t=token2&v=1.16.1&c=Tempus&size=500&id=al-1";

        assertEquals(IPv6StringLoader.cacheKey(first), IPv6StringLoader.cacheKey(second));
    }

    @Test
    public void theKeyCarriesNoCredentials() {
        String withPassword = BASE + "?u=me&p=enc:736563726574&v=1.16.1&c=Tempus&id=al-1";
        String withToken = BASE + "?u=me&s=salt&t=token&v=1.16.1&c=Tempus&id=al-1";

        assertEquals(BASE + "?u=me&v=1.16.1&c=Tempus&id=al-1", IPv6StringLoader.cacheKey(withPassword));
        assertEquals(BASE + "?u=me&v=1.16.1&c=Tempus&id=al-1", IPv6StringLoader.cacheKey(withToken));
    }

    @Test
    public void differentCoversSizesOrUsersKeepDifferentKeys() {
        String cover = BASE + "?u=me&s=salt&t=token&size=500&id=al-1";

        assertNotEquals(IPv6StringLoader.cacheKey(cover), IPv6StringLoader.cacheKey(cover.replace("al-1", "al-2")));
        assertNotEquals(IPv6StringLoader.cacheKey(cover), IPv6StringLoader.cacheKey(cover.replace("size=500", "size=300")));
        assertNotEquals(IPv6StringLoader.cacheKey(cover), IPv6StringLoader.cacheKey(cover.replace("u=me", "u=you")));
    }

    @Test
    public void urlsWithoutAuthAreUnchanged() {
        String radio = "https://radio.example.com/cover.png";

        assertEquals(radio, IPv6StringLoader.cacheKey(radio));
    }
}
