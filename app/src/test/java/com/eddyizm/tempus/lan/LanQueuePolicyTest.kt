package com.eddyizm.tempus.lan

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LanQueuePolicyTest {
    @Test fun staleQueueMustNotAuthorizePositionalEdits() {
        assertTrue(LanQueuePolicy.matchesRevision("current", "current"))
        assertFalse(LanQueuePolicy.matchesRevision("old", "current"))
        assertFalse(LanQueuePolicy.matchesRevision("", ""))
    }
    @Test fun emptyReceiverAcceptsAnInsertion() {
        LanQueuePolicy.validateInsertion(0, 1)
        LanQueuePolicy.validateInsertion(499, 1)
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateInsertion(500, 1) }
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateInsertion(Int.MAX_VALUE, 1) }
    }

    @Test fun playlistsMayContainRepeatedTracks() {
        LanQueuePolicy.validateSongs(listOf("one", "two", "one"))
        LanQueuePolicy.validateReorder(listOf("one", "two", "one"), listOf("one", "one", "two"))
        assertThrows(IllegalArgumentException::class.java) {
            LanQueuePolicy.validateReorder(listOf("one", "one", "two"), listOf("one", "two", "two"))
        }
    }

    @Test fun deletingLastTrackAndClearingEmptyQueueAreValid() {
        LanQueuePolicy.validateRange(1, 0, 1)
        LanQueuePolicy.validateRange(0, 0, 0)
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateRange(1, -1, 1) }
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateRange(1, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateRange(1, 0, 2) }
    }

    @Test fun movedTrackAndDestinationMustExist() {
        LanQueuePolicy.validateMove(2, 0, 1)
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateMove(0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateMove(2, 0, 2) }
    }

    @Test fun invalidSongBatchesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateSongs(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateSongs(listOf("")) }
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateSongs(List(501) { "id" }) }
        assertThrows(IllegalArgumentException::class.java) { LanQueuePolicy.validateSongs(listOf("ir_radio")) }
    }
}
