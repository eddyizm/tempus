package com.eddyizm.tempus.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MusicUtilAddressTest {

    // One address being a text prefix of the other is not the same as sitting under it. This
    // decides which timeout a ping gets, and a bare prefix test gave the public nas.duckdns.invalid
    // the shorter one meant for a local address.
    @Test
    public void treatsANeighboringHostnameAsADifferentServer() {
        assertFalse(MusicUtil.isUnderAddress("http://nas.duckdns.invalid/rest/ping", "http://nas"));
    }

    @Test
    public void matchesAnAddressAndAnythingUnderIt() {
        assertTrue(MusicUtil.isUnderAddress("http://nas/rest/ping", "http://nas"));
        assertTrue(MusicUtil.isUnderAddress("http://nas", "http://nas"));
    }

    @Test
    public void toleratesATrailingSlash() {
        assertTrue(MusicUtil.isUnderAddress("http://nas/rest/ping", "http://nas/"));
    }

    // A server behind a reverse proxy is reached at a subpath, and that subpath is part of the
    // address.
    @Test
    public void matchesAnAddressThatCarriesAPath() {
        assertTrue(MusicUtil.isUnderAddress(
                "https://example.invalid/navidrome/rest/ping", "https://example.invalid/navidrome"));
        assertFalse(MusicUtil.isUnderAddress(
                "https://example.invalid/other/rest/ping", "https://example.invalid/navidrome"));
    }

    @Test
    public void handlesAMissingAddress() {
        assertFalse(MusicUtil.isUnderAddress("http://nas/rest/ping", null));
        assertFalse(MusicUtil.isUnderAddress("http://nas/rest/ping", ""));
        assertFalse(MusicUtil.isUnderAddress(null, "http://nas"));
    }
}
