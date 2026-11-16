package com.eddyizm.tempus.util

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import okhttp3.internal.platform.Platform
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.X509TrustManager

object SelfSignedSslHelper {

    private val platformTrustManager: X509TrustManager = Platform.get().platformTrustManager()

    @SuppressLint("TrustAllX509TrustManager")
    private val permissiveTrustManager: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    @Volatile
    private var testAllowSelfSigned: Boolean? = null

    val permissiveHostnameVerifier: HostnameVerifier = HostnameVerifier { _, _ -> true }

    @JvmStatic
    fun isEnabled(): Boolean = testAllowSelfSigned ?: Preferences.isAllowSelfSigned()

    @JvmStatic
    fun effectiveTrustManager(): X509TrustManager =
        if (isEnabled()) permissiveTrustManager else platformTrustManager

    @JvmStatic
    fun setTestAllowSelfSigned(allow: Boolean?) {
        testAllowSelfSigned = allow
        ClientCertManager.refreshSslSocketFactory()
    }

    @JvmStatic
    fun applyHostnameVerifierIfEnabled(builder: OkHttpClient.Builder) {
        if (isEnabled()) {
            builder.hostnameVerifier(permissiveHostnameVerifier)
        }
    }

    @JvmStatic
    fun applyDefaultHostnameVerifier() {
        if (isEnabled()) {
            HttpsURLConnection.setDefaultHostnameVerifier(permissiveHostnameVerifier)
        } else {
            HttpsURLConnection.setDefaultHostnameVerifier(
                HttpsURLConnection.getDefaultHostnameVerifier()
            )
        }
    }
}
