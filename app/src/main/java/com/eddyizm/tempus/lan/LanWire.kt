package com.eddyizm.tempus.lan

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyInfo
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.*

/** Private experimental protocol: TLS plus certificate approval on both devices. */
object LanWire {
    const val TYPE = "_tempus-lan._tcp."
    const val MAX_FRAME = 131072
    private const val ALIAS = "tempus-lan-v1"

    @Synchronized fun context(): SSLContext = contextForAlias(ALIAS)

    /** Separate aliases let physical-device regression tests avoid the user's identity. */
    internal fun contextForAlias(alias: String): SSLContext {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? PrivateKey
        val compatible = existing?.let {
            val info = KeyFactory.getInstance(it.algorithm, "AndroidKeyStore").getKeySpec(it, KeyInfo::class.java)
            KeyProperties.DIGEST_NONE in info.digests && KeyProperties.ENCRYPTION_PADDING_NONE in info.encryptionPaddings
        } ?: false
        if (!compatible) {
            LanDiagnostics.record(if (existing == null) "Creating TLS identity" else "Replacing legacy TLS identity; pairing required again")
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    // Conscrypt hashes/pads TLS signatures itself, then asks Keystore to
                    // perform raw RSA (including RSA-PSS in TLS 1.3). These permissions
                    // do not disable TLS hashing, padding, encryption or peer approval.
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS).build())
                generateKeyPair()
            }
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, null) }
        val manager = managers.keyManagers.filterIsInstance<X509KeyManager>().first()
        // Only use this protocol's identity, even if the app owns other RSA keys.
        val identity = LanIdentityKeyManager(alias, manager)
        // Self-signed peers are authenticated at the application boundary by exact certificate
        // fingerprints. Unknown peers may only request pairing; never issue player commands.
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { require(chain.isNotEmpty()) }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { require(chain.isNotEmpty()) }
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf(identity), arrayOf(trust), null) }
    }

    fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun peer(socket: SSLSocket): String = fingerprint(socket.session.peerCertificates[0].encoded)
    fun own(socket: SSLSocket): String = fingerprint(socket.session.localCertificates[0].encoded)
    fun code(a: String, b: String): String = fingerprint(listOf(a, b).sorted().joinToString(":").toByteArray())
        .take(12).uppercase().chunked(4).joinToString(" ")

    fun read(socket: SSLSocket): JSONObject {
        val input = DataInputStream(socket.inputStream)
        val size = input.readInt()
        require(size in 1..MAX_FRAME) { "Invalid frame size" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    fun write(socket: SSLSocket, value: JSONObject) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_FRAME)
        DataOutputStream(socket.outputStream).apply { writeInt(bytes.size); write(bytes); flush() }
    }
}
