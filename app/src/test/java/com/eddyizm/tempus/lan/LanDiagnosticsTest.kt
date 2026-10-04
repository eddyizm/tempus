package com.eddyizm.tempus.lan

import org.junit.Assert.*
import org.junit.Test

class LanDiagnosticsTest {
    @Test fun recordsCausesWithoutPrivateExceptionMessages() {
        LanDiagnostics.record("test failure", IllegalStateException("private-server-url",
            IllegalArgumentException("private-password")))
        val report = LanDiagnostics.snapshot()
        assertTrue(report.contains("IllegalStateException -> IllegalArgumentException"))
        assertFalse(report.contains("private-server-url"))
        assertFalse(report.contains("private-password"))
    }

    @Test fun retainsOnlyMostRecentEvents() {
        repeat(201) { LanDiagnostics.record("bounded-event-$it") }
        val lines = LanDiagnostics.snapshot().lines()
        assertEquals(200, lines.size)
        assertTrue(lines.first().endsWith("bounded-event-1"))
        assertTrue(lines.last().endsWith("bounded-event-200"))
    }
}
