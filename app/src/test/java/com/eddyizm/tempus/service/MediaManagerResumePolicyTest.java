package com.eddyizm.tempus.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaManagerResumePolicyTest {

    @Test
    public void isResumable_onlyForPodcastAudiobookOrLongTracks() {
        assertTrue(MediaManager.isResumable("podcast", 0));
        assertTrue(MediaManager.isResumable("audiobook", 0));
        assertTrue(MediaManager.isResumable("music", 11L * 60L * 1000L));
        assertFalse(MediaManager.isResumable("music", 3L * 60L * 1000L));
        assertFalse(MediaManager.isResumable("radio", 60L * 60L * 1000L));
    }
}
