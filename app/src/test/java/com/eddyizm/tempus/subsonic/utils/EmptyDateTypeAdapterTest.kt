package com.eddyizm.tempus.subsonic.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmptyDateTypeAdapterTest {

    private val base = 1_759_341_753_000L // 2025-10-01T18:02:33Z

    private fun millis(value: String) = EmptyDateTypeAdapter.parse(value)!!.time

    @Test
    fun parsesWithoutFraction() {
        assertEquals(base, millis("2025-10-01T18:02:33Z"))
    }

    @Test
    fun parsesAnyNumberOfFractionDigits() {
        assertEquals(base + 500, millis("2025-10-01T18:02:33.5Z"))
        assertEquals(base + 123, millis("2025-10-01T18:02:33.123Z"))
        assertEquals(base + 123, millis("2025-10-01T18:02:33.123456789Z"))
    }

    @Test
    fun appliesOffsets() {
        assertEquals(base, millis("2025-10-01T20:02:33+02:00"))
        assertEquals(base, millis("2025-10-01T13:32:33-0430"))
    }

    // The old SimpleDateFormat patterns expected a literal Z, so these fell through to the
    // seconds-only pattern and were read as UTC: 5 hours too old and 2 hours too new.
    @Test
    fun appliesOffsetsWithFraction() {
        assertEquals(base + 123, millis("2025-10-01T13:02:33.123456-05:00"))
        assertEquals(base + 500, millis("2025-10-01T20:02:33.5+02:00"))
    }

    @Test
    fun treatsMissingZoneAsUtc() {
        assertEquals(base, millis("2025-10-01T18:02:33"))
    }

    @Test
    fun returnsNullForEmptyOrInvalid() {
        assertNull(EmptyDateTypeAdapter.parse(""))
        assertNull(EmptyDateTypeAdapter.parse("   "))
        assertNull(EmptyDateTypeAdapter.parse("not a date"))
        assertNull(EmptyDateTypeAdapter.parse("2025-13-01T18:02:33Z"))
    }
}
