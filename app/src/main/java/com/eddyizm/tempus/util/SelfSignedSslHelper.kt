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
 * Trust is narrowed to a user-approved certificate pin for the configured server.
 * Hostname verification remains enabled/default. Login "test connection" can scope trust
 * overrides to that specific test client only (no process-wide default SSL/HV changes).
 */
object SelfSignedSslHelper {

    data class TrustConfig(
        val allowSelfSigned: Boolean,
        val trustedServerCert: String?
    )

    class UnapprovedCertificateException(
        val certificatePin: String,
        message: String = "Server certificate is not trusted yet"
    ) : CertificateException(message)

    private val platformTrustManager: X509TrustManager = Platform.get().platformTrustManager()

    @Volatile
    private var pendingApprovalCert: String? = null

    @JvmStatic
    fun isEnabled(): Boolean = Preferences.isAllowSelfSigned()

    @JvmStatic
    fun getTrustedServerCert(): String? = Preferences.getTrustedServerCert()

    /** Approved pin only applies when self-signed trust is explicitly enabled for the server. */
    @JvmStatic
    fun effectiveTrustedServerCert(allowSelfSigned: Boolean, trustedServerCert: String?): String? =
        if (allowSelfSigned) trustedServerCert?.takeIf { it.isNotBlank() } else null

    /** Trust config for a login-test connection, optionally overriding with a user-approved pin. */
    @JvmStatic
    fun testTrustConfig(
        allowSelfSigned: Boolean,
        trustedServerCert: String?,
        approvedPinOverride: String? = null
    ): TrustConfig = TrustConfig(
        allowSelfSigned,
        effectiveTrustedServerCert(allowSelfSigned, approvedPinOverride ?: trustedServerCert)
    )

    @JvmStatic
    fun formatFingerprint(certificatePin: String): String = certificatePin.chunked(4).joinToString(" ")

    /** Trust manager for normal app traffic based on active server preferences. */
    @JvmStatic
    fun effectiveTrustManager(): X509TrustManager = createTrustManager(null)

    /**
     * Trust manager for a specific connection. When [config] is provided, it scopes
     * self-signed acceptance to that connection (used for login test) without affecting
     * process defaults.
     */
    @JvmStatic
    fun createTrustManager(config: TrustConfig?): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            platformTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val serverChain = chain?.map { it as X509Certificate }?.toTypedArray()
            val allowSelfSigned = config?.allowSelfSigned ?: Preferences.isAllowSelfSigned()
            val expectedPin = effectiveTrustedServerCert(
                allowSelfSigned,
                config?.trustedServerCert ?: Preferences.getTrustedServerCert()
            )

            try {
                platformTrustManager.checkServerTrusted(serverChain, authType)
                return
            } catch (e: CertificateException) {
                if (!allowSelfSigned || serverChain.isNullOrEmpty()) throw e
            }

            val leaf = serverChain!!.first()
            val presentedPin = certificatePin(leaf)

            if (expectedPin == null) {
                // Require explicit user approval before trusting/sending credentials.
                // For scoped login-test connections, expose the pin so UI can approve and retry.
                if (config != null) {
                    pendingApprovalCert = presentedPin
                    throw UnapprovedCertificateException(presentedPin)
                }
                throw CertificateException("Server certificate is not trusted")
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
        pendingApprovalCert = null
    }

    @JvmStatic
    fun clearPendingApprovalCert() {
        pendingApprovalCert = null
    }

    @JvmStatic
    fun consumePendingApprovalCert(): String? {
        val cert = pendingApprovalCert
        pendingApprovalCert = null
        return cert
    }

    @JvmStatic
    fun certificatePin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
