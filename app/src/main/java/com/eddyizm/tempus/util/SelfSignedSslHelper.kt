package com.eddyizm.tempus.util

import okhttp3.OkHttpClient
import okhttp3.internal.platform.Platform
import android.util.Base64
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.X509TrustManager

object SelfSignedSslHelper {

    private data class TestTrustContext(
        val allowSelfSigned: Boolean,
        val trustedServerCert: String?
    )

    private val platformTrustManager: X509TrustManager = Platform.get().platformTrustManager()

    @Volatile
    private var testContext: TestTrustContext? = null

    @Volatile
    private var capturedTrustedCert: String? = null

    private val pinningTrustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            platformTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val serverChain = chain?.map { it as X509Certificate }?.toTypedArray()
            try {
                platformTrustManager.checkServerTrusted(serverChain, authType)
                return
            } catch (e: CertificateException) {
                if (!isEnabled() || serverChain.isNullOrEmpty()) throw e
            }

            val leaf = serverChain!!.first()
            val presentedPin = certificatePin(leaf)
            val expectedPin = getTrustedServerCert()?.takeIf { it.isNotBlank() }

            if (expectedPin == null) {
                // Explicit opt-in (per-server allow self-signed): trust and capture this certificate
                // as the server's accepted anchor for subsequent connections.
                capturedTrustedCert = presentedPin
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
    fun isEnabled(): Boolean = testContext?.allowSelfSigned ?: Preferences.isAllowSelfSigned()

    @JvmStatic
    fun getTrustedServerCert(): String? = testContext?.trustedServerCert ?: Preferences.getTrustedServerCert()

    @JvmStatic
    fun effectiveTrustManager(): X509TrustManager = pinningTrustManager

    @JvmStatic
    fun setTestTrustContext(allowSelfSigned: Boolean, trustedServerCert: String?) {
        capturedTrustedCert = null
        testContext = TestTrustContext(allowSelfSigned, trustedServerCert)
        ClientCertManager.refreshSslSocketFactory()
    }

    @JvmStatic
    fun clearTestTrustContext() {
        capturedTrustedCert = null
        testContext = null
        ClientCertManager.refreshSslSocketFactory()
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

    @JvmStatic
    fun applyHostnameVerifierIfEnabled(builder: OkHttpClient.Builder) {
        // Keep hostname verification enabled; trust is narrowed to the accepted certificate anchor.
    }

    @JvmStatic
    fun applyDefaultHostnameVerifier() {
        HttpsURLConnection.setDefaultHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
    }
}
