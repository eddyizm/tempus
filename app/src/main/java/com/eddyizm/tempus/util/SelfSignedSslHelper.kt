package com.eddyizm.tempus.util

import android.util.Base64
import okhttp3.internal.platform.Platform
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Per-server trust decisions for explicitly opted-in self-signed certificates.
 *
 * Normal app traffic uses the active server preferences. Login "test connection" must scope trust
 * overrides to that specific connection/client only (no process-wide default SSL/HV changes).
 */
object SelfSignedSslHelper {

    data class TrustConfig(
        val allowSelfSigned: Boolean,
        val trustedServerCert: String?
    )

    private val platformTrustManager: X509TrustManager = Platform.get().platformTrustManager()

    @Volatile
    private var capturedTrustedCert: String? = null

    @JvmStatic
    fun isEnabled(): Boolean = Preferences.isAllowSelfSigned()

    @JvmStatic
    fun getTrustedServerCert(): String? = Preferences.getTrustedServerCert()

    /** Trust manager for normal app traffic based on active server preferences. */
    @JvmStatic
    fun effectiveTrustManager(): X509TrustManager = createTrustManager(null)

    /**
     * Trust manager for a specific connection. When [config] is provided, it scopes self-signed
     * acceptance/capture to that connection (used for login test) without affecting process defaults.
     */
    @JvmStatic
    fun createTrustManager(config: TrustConfig?): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            platformTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val serverChain = chain?.map { it as X509Certificate }?.toTypedArray()
            val allowSelfSigned = config?.allowSelfSigned ?: Preferences.isAllowSelfSigned()
            val expectedPin = (config?.trustedServerCert ?: Preferences.getTrustedServerCert())
                ?.takeIf { it.isNotBlank() }

            try {
                platformTrustManager.checkServerTrusted(serverChain, authType)
                return
            } catch (e: CertificateException) {
                if (!allowSelfSigned || serverChain.isNullOrEmpty()) throw e
            }

            val leaf = serverChain!!.first()
            val presentedPin = certificatePin(leaf)

            if (expectedPin == null) {
                // Explicit opt-in: accept and capture this certificate as the server's anchor for
                // subsequent connections (caller persists it on success).
                if (config != null) {
                    capturedTrustedCert = presentedPin
                }
                return
            }

            if (expectedPin == presentedPin) {
                return
            }

            throw CertificateException("Server certificate does not match the trusted certificate")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = platformTrustManager.acceptedIssuers
    }

    @JvmStatic
    fun beginTrustCapture() {
        capturedTrustedCert = null
    }

    @JvmStatic
    fun clearCapturedTrustedCert() {
        capturedTrustedCert = null
    }

    @JvmStatic
    fun consumeCapturedTrustedCert(): String? {
        val cert = capturedTrustedCert
        capturedTrustedCert = null
        return cert
    }

    @JvmStatic
    fun certificatePin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
