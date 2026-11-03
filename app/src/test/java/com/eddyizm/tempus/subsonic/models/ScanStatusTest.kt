package com.eddyizm.tempus.subsonic.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanStatusTest {

    @Test
    fun readsScanningFlagFromServerJson() {
        val status = Gson().fromJson("""{"scanning":true,"count":5}""", ScanStatus::class.java)

        assertTrue(status.isScanning)
        assertEquals(5L, status.count)
    }

    @Test
    fun readsFinishedScan() {
        val status = Gson().fromJson("""{"scanning":false,"count":2659}""", ScanStatus::class.java)

        assertFalse(status.isScanning)
        assertEquals(2659L, status.count)
    }
}
