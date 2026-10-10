package com.eddyizm.tempus.lan

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import javax.net.ssl.X509KeyManager

class LanIdentityKeyManagerTest {
    @Test fun selectsOnlyLanIdentityForSocketsAndEngines() {
        val delegate = mock(X509KeyManager::class.java)
        `when`(delegate.getClientAliases("RSA", null)).thenReturn(arrayOf("unrelated", "lan"))
        `when`(delegate.getServerAliases("RSA", null)).thenReturn(arrayOf("unrelated", "lan"))
        val manager = LanIdentityKeyManager("lan", delegate)
        assertEquals("lan", manager.chooseClientAlias(arrayOf("EC", "RSA"), null, null))
        assertEquals("lan", manager.chooseServerAlias("RSA", null, null))
        assertEquals("lan", manager.chooseEngineClientAlias(arrayOf("RSA"), null, null))
        assertEquals("lan", manager.chooseEngineServerAlias("RSA", null, null))
        assertNull(manager.getPrivateKey("unrelated"))
        assertNull(manager.getCertificateChain("unrelated"))
    }

    @Test fun refusesToSubstituteAnUnrelatedKeyWhenLanIdentityIsNotCompatible() {
        val delegate = mock(X509KeyManager::class.java)
        `when`(delegate.getClientAliases("RSA", null)).thenReturn(arrayOf("unrelated"))
        `when`(delegate.getServerAliases("RSA", null)).thenReturn(arrayOf("unrelated"))
        val manager = LanIdentityKeyManager("lan", delegate)
        assertNull(manager.chooseClientAlias(arrayOf("RSA"), null, null))
        assertNull(manager.chooseServerAlias("RSA", null, null))
        assertNull(manager.chooseEngineClientAlias(arrayOf("RSA"), null, null))
        assertNull(manager.chooseEngineServerAlias("RSA", null, null))
    }
}
