package com.eddyizm.tempus.util

import com.eddyizm.tempus.util.Preferences.ResumePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePointsCapTest {

    private fun point(id: String, timestamp: Long) =
        ResumePoint(id, 1000L, "title", "album", "artist", "albumId", "coverArtId", null, timestamp)

    @Test
    fun keepsOnlyTheNewestWhenOverCap() {
        val points = HashMap<String, ResumePoint>()
        for (i in 1..250) points["id$i"] = point("id$i", i.toLong())

        capResumePoints(points, 200)

        assertEquals(200, points.size)
        // The newest 200 are id51..id250.
        assertTrue(points.containsKey("id250"))
        assertTrue(points.containsKey("id51"))
        assertFalse(points.containsKey("id50"))
        assertFalse(points.containsKey("id1"))
    }

    @Test
    fun leavesAStoreUnderCapAlone() {
        val points = HashMap<String, ResumePoint>()
        points["a"] = point("a", 1L)
        points["b"] = point("b", 2L)

        capResumePoints(points, 200)

        assertEquals(2, points.size)
    }
}
