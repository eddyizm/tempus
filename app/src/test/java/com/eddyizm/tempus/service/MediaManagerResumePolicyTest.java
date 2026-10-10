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

    @Test
    public void continueListening_savesEligibleTracksWhileTheSwitchIsOn() {
        assertTrue(MediaManager.savesToContinueListening("podcast", 0, true));
        assertTrue(MediaManager.savesToContinueListening("music", 11L * 60L * 1000L, true));
    }

    @Test
    public void continueListening_savesNothingWhileTheSwitchIsOff() {
        // Off means no entry and no server bookmark, even for a track that would qualify.
        assertFalse(MediaManager.savesToContinueListening("podcast", 0, false));
        assertFalse(MediaManager.savesToContinueListening("music", 11L * 60L * 1000L, false));
    }

    @Test
    public void continueListening_switchDoesNotWidenEligibility() {
        assertFalse(MediaManager.savesToContinueListening("music", 3L * 60L * 1000L, true));
        assertFalse(MediaManager.savesToContinueListening("radio", 60L * 60L * 1000L, true));
    }
}
