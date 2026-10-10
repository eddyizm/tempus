package com.eddyizm.tempus.lan

import org.junit.Assert.assertEquals
import org.junit.Test

class LanRemoteStateTest {
    private val playing = LanRemoteState(
        active = true, connected = true, playing = true,
        position = 12000, duration = 60000, receivedAt = 1000
    )

    @Test fun playingPositionAdvancesBetweenReceiverUpdates() {
        assertEquals(13500L, playing.positionAt(2500))
    }
    @Test fun positionFollowsReceiverPlaybackSpeed() {
        assertEquals(15000L, playing.copy(speed = 2f).positionAt(2500))
        assertEquals(12750L, playing.copy(speed = 0.5f).positionAt(2500))
    }

    @Test fun pauseAndNetworkLossFreezeTheLastReportedPosition() {
        assertEquals(12000L, playing.copy(playing = false).positionAt(5000))
        assertEquals(12000L, playing.copy(connected = false).positionAt(5000))
    }

    @Test fun PositionNeverExceedsDurationOrMovesBackBeforeSnapshot() {
        assertEquals(60000L, playing.positionAt(100000))
        assertEquals(12000L, playing.positionAt(500))
        assertEquals(0L, playing.copy(duration = -1).positionAt(2500))
    }

    @Test fun FreshTrackSnapshotReplacesThePreviousPosition() {
        val next = playing.copy(position = 0, duration = 90000, receivedAt = 5000)
        assertEquals(500L, next.positionAt(5500))
    }
}
