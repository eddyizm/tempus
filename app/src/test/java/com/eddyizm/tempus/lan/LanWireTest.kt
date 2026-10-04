package com.eddyizm.tempus.lan

import org.junit.Assert.*
import org.junit.Test

class LanWireTest {
    @Test fun certificateFingerprintUsesSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            LanWire.fingerprint("abc".toByteArray()))
    }

    @Test fun pairingCodeIsIdenticalOnBothPhones() {
        val receiver = LanWire.fingerprint("receiver-certificate".toByteArray())
        val controller = LanWire.fingerprint("controller-certificate".toByteArray())
        assertEquals(LanWire.code(receiver, controller), LanWire.code(controller, receiver))
        assertTrue(LanWire.code(receiver, controller).matches(Regex("[0-9A-F]{4} [0-9A-F]{4} [0-9A-F]{4}")))
    }

    @Test fun substitutingEitherCertificateChangesPairingCode() {
        assertNotEquals(LanWire.code("receiver", "controller"), LanWire.code("attacker", "controller"))
        assertNotEquals(LanWire.code("receiver", "controller"), LanWire.code("receiver", "attacker"))
    }
}
