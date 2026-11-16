package com.eddyizm.tempus.util

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.internal.platform.Platform
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
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

    /**
     * Token-bound approval session for a single login "test connection" attempt/flow.
     * Prevents global pending-approval state from leaking across unrelated tests.
     */
    class LoginTestSession internal constructor(
        val token: String,
        val config: TrustConfig
    ) {
        internal val pendingApprovalPin = AtomicReference<String?>()

        fun capturePendingPin(pin: String) {
            pendingApprovalPin.compareAndSet(null, pin)
        }

        fun consumePendingApprovalPin(): String? = pendingApprovalPin.getAndSet(null)

        fun approvedConfig(approvedPin: String): TrustConfig = testTrustConfig(
            config.allowSelfSigned,
            approvedPin
        )
    }

    private val platformTrustManager: X509TrustManager = Platform.get().platformTrustManager()

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
    fun testTrustConfig(allowSelfSigned: Boolean, trustedServerCert: String?): TrustConfig =
        testTrustConfig(allowSelfSigned, trustedServerCert, null)

    @JvmStatic
    fun testTrustConfig(
        allowSelfSigned: Boolean,
        trustedServerCert: String?,
        approvedPinOverride: String?
    ): TrustConfig = TrustConfig(
        allowSelfSigned,
        effectiveTrustedServerCert(allowSelfSigned, approvedPinOverride ?: trustedServerCert)
    )

    @JvmStatic
    fun beginLoginTestSession(
        allowSelfSigned: Boolean,
        trustedServerCert: String?,
        approvedPinOverride: String? = null
    ): LoginTestSession = LoginTestSession(
        UUID.randomUUID().toString(),
        testTrustConfig(allowSelfSigned, trustedServerCert, approvedPinOverride)
    )

    @JvmStatic
    fun formatFingerprint(certificatePin: String): String = certificatePin.chunked(4).joinToString(" ")

    /** Trust manager for normal app traffic based on active server preferences. */
    @JvmStatic
    fun effectiveTrustManager(): X509TrustManager = createTrustManager(null, null)

    /**
     * Trust manager for a specific connection. When [config] is provided, it scopes
     * self-signed acceptance to that connection (used for login test) without affecting
     * process defaults.
     */
    @JvmStatic
    fun createTrustManager(config: TrustConfig?): X509TrustManager = createTrustManager(config, null)

    @JvmStatic
    fun createTrustManager(config: TrustConfig?, session: LoginTestSession?): X509TrustManager = object : X509TrustManager {
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
                // For scoped login-test sessions, expose the pin so UI can approve and retry.
                if (session != null) {
                    session.capturePendingPin(presentedPin)
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

    /**
     * Apply SSL materials to an OkHttp client builder.
     * - [config] null => active server policy (uses ClientCertManager's current socket factory/trust manager)
     * - [config] non-null => scoped test policy for this client only
     */
    @JvmStatic
    @JvmOverloads
    fun applySsl(builder: OkHttpClient.Builder, config: TrustConfig?, session: LoginTestSession? = null): OkHttpClient.Builder {
        if (config != null) {
            val trustManager = createTrustManager(config, session)
            ClientCertManager.createSslSocketFactory(trustManager)?.let { sslSocketFactory ->
                // Scoped to this client only; hostname verification remains enabled.
                builder.sslSocketFactory(sslSocketFactory, trustManager)
            }
            return builder
        }

        ClientCertManager.sslSocketFactory?.let { sslSocketFactory ->
            // Keep hostname verification enabled (default). Trust decisions are delegated to the
            // current trust manager (including pinned accepted certs for opted-in servers).
            builder.sslSocketFactory(sslSocketFactory, ClientCertManager.trustManager)
        }
        return builder
    }

    @JvmStatic
    fun certificatePin(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /** Persist active-server self-signed trust settings and refresh TLS policy/clients. */
    @JvmStatic
    fun applyActiveServerTlsSettings(allowSelfSigned: Boolean, trustedServerCert: String?) {
        Preferences.setAllowSelfSigned(allowSelfSigned)
        Preferences.setTrustedServerCert(effectiveTrustedServerCert(allowSelfSigned, trustedServerCert))
        ClientCertManager.refreshSslSocketFactory()
    }

    /**
     * Notify dependents that TLS policy changed (socket factory/trust decisions).
     * Decouples policy-change notification from ClientCertManager internals.
     */
    @JvmStatic
    fun notifyTlsPolicyChanged() {
        DownloadUtil.invalidateTlsConnections()
    }
}
