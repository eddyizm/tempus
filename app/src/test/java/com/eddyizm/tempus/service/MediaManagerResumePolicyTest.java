package com.eddyizm.tempus.service;

import static org.junit.Assert.assertEquals;
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
        assertFalse(MediaManager.savesToContinueListening("podcast", 0, false));
        assertFalse(MediaManager.savesToContinueListening("music", 11L * 60L * 1000L, false));
    }

    @Test
    public void continueListening_switchDoesNotWidenEligibility() {
        assertFalse(MediaManager.savesToContinueListening("music", 3L * 60L * 1000L, true));
        assertFalse(MediaManager.savesToContinueListening("radio", 60L * 60L * 1000L, true));
    }

    @Test
    public void resumePosition_prefersTheContinueListeningEntryWhileTheSwitchIsOn() {
        assertEquals(362_000L, MediaManager.resumePosition(362_000L, 664_000L, true));
    }

    @Test
    public void resumePosition_ignoresAnEntryLeftFromBeforeTheSwitchWentOff() {
        assertEquals(664_000L, MediaManager.resumePosition(362_000L, 664_000L, false));
    }

    @Test
    public void resumePosition_fallsBackToTheQueueRowWithoutAnEntry() {
        assertEquals(664_000L, MediaManager.resumePosition(0L, 664_000L, true));
        assertEquals(0L, MediaManager.resumePosition(0L, -1L, true));
    }
}
