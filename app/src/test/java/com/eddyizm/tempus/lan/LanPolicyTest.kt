package com.eddyizm.tempus.lan

import org.junit.Assert.*
import org.junit.Test

class LanPolicyTest {
    @Test fun pairingWindowDoesNotAuthorizePlaybackCommands() {
        assertTrue(LanPolicy.mayPair("hello", 100, 200))
        for (command in listOf("queue", "edit", "queue_state", "watch", "play", "pause", "volume", "device_volume", "shuffle", "repeat", "speed", "timer", "status", "")) {
            assertFalse(LanPolicy.mayPair(command, 100, 200))
        }
        assertFalse(LanPolicy.mayPair("hello", 200, 200))
        assertFalse(LanPolicy.mayPair("hello", 300, 200))
    }

    @Test fun pairingApprovalRequiresAnOpenWindow() {
        assertTrue(LanPolicy.mayApprove(100, 200, 90))
        assertFalse(LanPolicy.mayApprove(100, 0, 90))
        assertFalse(LanPolicy.mayApprove(200, 200, 90))
    }

    @Test fun reopeningPairingDoesNotAuthorizeExpiredRequests() {
        assertFalse(LanPolicy.mayApprove(120100, 240100, 100))
        assertTrue(LanPolicy.mayApprove(120099, 240100, 100))
        assertFalse(LanPolicy.mayApprove(100, 200, 101))
    }

    @Test fun queueBoundsAndPersistableIdentityAreValidated() {
        LanPolicy.validateQueue(listOf("one", "two"), 1, 1234)
        LanPolicy.validateQueue((1..500).map { "$it" }, 499, 0)
        val invalid = listOf<() -> Unit>(
            { LanPolicy.validateQueue(emptyList(), 0, 0) },
            { LanPolicy.validateQueue(listOf("one"), 1, 0) },
            { LanPolicy.validateQueue(listOf("one"), -1, 0) },
            { LanPolicy.validateQueue(listOf("one"), 0, -1) },
            { LanPolicy.validateQueue(listOf(""), 0, 0) },
            { LanPolicy.validateQueue(listOf("one", "one"), 0, 0) },
            { LanPolicy.validateQueue((1..501).map { "$it" }, 0, 0) }
        )
        invalid.forEach { check -> assertThrows(IllegalArgumentException::class.java) { check() } }
    }
}
