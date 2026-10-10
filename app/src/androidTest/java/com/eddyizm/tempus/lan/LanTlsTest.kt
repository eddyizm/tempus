package com.eddyizm.tempus.lan

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Run on physical phones: desktop JVM tests cannot exercise Android Keystore. */
@RunWith(AndroidJUnit4::class)
class LanTlsTest {
    @Test fun migratesLegacyIdentityAndAuthenticatesBothTlsPeers() {
        val serverAlias = "tempus-lan-test-${UUID.randomUUID()}"
        val clientAlias = "tempus-lan-test-${UUID.randomUUID()}"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(serverAlias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS).build())
                generateKeyPair()
            }
            val legacy = store.getCertificate(serverAlias).encoded
            val serverContext = LanWire.contextForAlias(serverAlias)
            val replacement = store.getCertificate(serverAlias).encoded
            assertFalse(legacy.contentEquals(replacement))
            LanWire.contextForAlias(serverAlias)
            assertArrayEquals(replacement, store.getCertificate(serverAlias).encoded)
            val clientContext = LanWire.contextForAlias(clientAlias)
            val clientCertificate = store.getCertificate(clientAlias).encoded
            val protocols = clientContext.supportedSSLParameters.protocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }
            assertTrue(protocols.contains("TLSv1.2"))
            for (protocol in protocols) {
                (serverContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket).use { listener ->
                    listener.enabledProtocols = arrayOf(protocol)
                    listener.needClientAuth = true
                    listener.soTimeout = 10000
                    val result = executor.submit<String> {
                        (listener.accept() as SSLSocket).use { socket ->
                            socket.soTimeout = 10000
                            socket.startHandshake()
                            socket.outputStream.write(42)
                            LanWire.peer(socket)
                        }
                    }
                    (clientContext.socketFactory.createSocket() as SSLSocket).use { socket ->
                        socket.enabledProtocols = arrayOf(protocol)
                        socket.soTimeout = 10000
                        socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), listener.localPort), 10000)
                        socket.startHandshake()
                        assertEquals(protocol, socket.session.protocol)
                        assertEquals(LanWire.fingerprint(replacement), LanWire.peer(socket))
                        assertEquals(42, socket.inputStream.read())
                    }
                    assertEquals(LanWire.fingerprint(clientCertificate), result.get(15, TimeUnit.SECONDS))
                }
            }
        } finally {
            executor.shutdownNow()
            store.deleteEntry(serverAlias)
            store.deleteEntry(clientAlias)
        }
    }
}
