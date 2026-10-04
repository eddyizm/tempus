package com.eddyizm.tempus.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpnpPlayerTest {

    @Test
    fun aReportedPositionBecomesMilliseconds() {
        assertEquals(118_000L, UpnpPlayer.asMillis("00:01:58"))
        assertEquals(431_000L, UpnpPlayer.asMillis("00:07:11"))
        assertEquals(3_661_000L, UpnpPlayer.asMillis("01:01:01"))
        assertEquals(0L, UpnpPlayer.asMillis("00:00:00"))
    }

    @Test
    fun theFractionalAndSingleDigitHourFormsAreBothRead() {
        // Both appear in the wild, and neither is worth a second code path.
        assertEquals(90_000L, UpnpPlayer.asMillis("0:01:30.000"))
        assertEquals(90_000L, UpnpPlayer.asMillis(" 00:01:30 "))
    }

    @Test
    fun somethingThatIsNotAClockIsNotGuessedAt() {
        // NOT_IMPLEMENTED is a real answer, not a failure. Reading it as zero drags the bar to the start.
        assertNull(UpnpPlayer.asMillis("NOT_IMPLEMENTED"))
        assertNull(UpnpPlayer.asMillis(null))
        assertNull(UpnpPlayer.asMillis(""))
        assertNull(UpnpPlayer.asMillis("01:30"))
        assertNull(UpnpPlayer.asMillis("aa:bb:cc"))
        assertNull(UpnpPlayer.asMillis("00:99:00"))
        assertNull(UpnpPlayer.asMillis("00:00:99"))
    }

    @Test
    fun aSeekTargetIsWrittenAsTheClockARendererExpects() {
        assertEquals("00:01:30", UpnpPlayer.asClock(90_000))
        assertEquals("00:00:00", UpnpPlayer.asClock(0))
        assertEquals("01:01:01", UpnpPlayer.asClock(3_661_000))
        assertEquals("10:00:00", UpnpPlayer.asClock(36_000_000))
    }

    @Test
    fun aPositionBeforeTheStartIsClampedInsteadOfWrittenNegative() {
        assertEquals("00:00:00", UpnpPlayer.asClock(-5_000))
    }

    @Test
    fun aPositionSurvivesTheRoundTrip() {
        for (seconds in listOf(0L, 1L, 59L, 60L, 3599L, 3600L, 7_231L)) {
            assertEquals(seconds * 1000, UpnpPlayer.asMillis(UpnpPlayer.asClock(seconds * 1000)))
        }
    }
}
