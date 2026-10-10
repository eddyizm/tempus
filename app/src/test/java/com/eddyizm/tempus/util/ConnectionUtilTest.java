package com.eddyizm.tempus.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnectionUtilTest {

    // One address being a text prefix of the other is not the same as sitting under it. This
    // decides which timeout a ping gets, and a bare prefix test gave the public nas.duckdns.invalid
    // the shorter one meant for a local address.
    @Test
    public void treatsANeighboringHostnameAsADifferentServer() {
        assertFalse(ConnectionUtil.isUnderAddress("http://nas.duckdns.invalid/rest/ping", "http://nas"));
    }

    @Test
    public void matchesAnAddressAndAnythingUnderIt() {
        assertTrue(ConnectionUtil.isUnderAddress("http://nas/rest/ping", "http://nas"));
        assertTrue(ConnectionUtil.isUnderAddress("http://nas", "http://nas"));
    }

    @Test
    public void toleratesATrailingSlash() {
        assertTrue(ConnectionUtil.isUnderAddress("http://nas/rest/ping", "http://nas/"));
    }

    // A server behind a reverse proxy is reached at a subpath, and that subpath is part of the
    // address.
    @Test
    public void matchesAnAddressThatCarriesAPath() {
        assertTrue(ConnectionUtil.isUnderAddress(
                "https://example.invalid/navidrome/rest/ping", "https://example.invalid/navidrome"));
        assertFalse(ConnectionUtil.isUnderAddress(
                "https://example.invalid/other/rest/ping", "https://example.invalid/navidrome"));
    }

    @Test
    public void handlesAMissingAddress() {
        assertFalse(ConnectionUtil.isUnderAddress("http://nas/rest/ping", null));
        assertFalse(ConnectionUtil.isUnderAddress("http://nas/rest/ping", ""));
        assertFalse(ConnectionUtil.isUnderAddress(null, "http://nas"));
    }
}
