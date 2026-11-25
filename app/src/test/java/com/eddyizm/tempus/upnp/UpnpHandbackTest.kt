package com.eddyizm.tempus.upnp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpHandbackTest {

    @Test
    fun switchHeardFirstKeepsPlaying() {
        val handback = UpnpHandback()
        assertFalse(handback.switchedAway(nowMs = 1_000))
        assertTrue(handback.rendererLeft(wasPlaying = true, nowMs = 1_010))
    }

    @Test
    fun switchHeardSecondResumes() {
        val handback = UpnpHandback()
        assertFalse(handback.rendererLeft(wasPlaying = true, nowMs = 1_000))
        assertTrue(handback.switchedAway(nowMs = 1_010))
    }

    @Test
    fun stopPauses() {
        val handback = UpnpHandback()
        assertFalse(handback.rendererLeft(wasPlaying = true, nowMs = 1_000))
    }

    @Test
    fun switchWhilePausedStaysPaused() {
        val handback = UpnpHandback()
        assertFalse(handback.switchedAway(nowMs = 1_000))
        assertFalse(handback.rendererLeft(wasPlaying = false, nowMs = 1_010))

        val other = UpnpHandback()
        assertFalse(other.rendererLeft(wasPlaying = false, nowMs = 1_000))
        assertFalse(other.switchedAway(nowMs = 1_010))
    }

    @Test
    fun staleSwitchDoesNotResumeALaterStop() {
        val handback = UpnpHandback(windowMs = 3_000)
        handback.switchedAway(nowMs = 1_000)
        assertFalse(handback.rendererLeft(wasPlaying = true, nowMs = 10_000))
    }

    @Test
    fun staleStopIsNotResumedByALaterSwitch() {
        val handback = UpnpHandback(windowMs = 3_000)
        handback.rendererLeft(wasPlaying = true, nowMs = 1_000)
        assertFalse(handback.switchedAway(nowMs = 10_000))
    }

    @Test
    fun eachSignalIsUsedOnce() {
        val handback = UpnpHandback()
        handback.switchedAway(nowMs = 1_000)
        assertTrue(handback.rendererLeft(wasPlaying = true, nowMs = 1_010))
        // The next cast's stop must not ride on the switch already spent.
        assertFalse(handback.rendererLeft(wasPlaying = true, nowMs = 1_500))
    }
}
