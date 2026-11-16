package com.eddyizm.tempus.util

import android.content.Context
import android.security.KeyChain
import android.util.Log
import androidx.core.net.toUri
import java.net.Socket
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

object ClientCertManager {

    private const val TAG = "ClientCertManager"

    private var appContext: Context? = null

    val trustManager: X509TrustManager
        get() = SelfSignedSslHelper.effectiveTrustManager()

    var sslSocketFactory: SSLSocketFactory? = null
        private set

    @JvmStatic
    fun setupSslSocketFactory(context: Context) {
        appContext = context.applicationContext
        refreshSslSocketFactory()
    }

    @JvmStatic
    fun refreshSslSocketFactory() {
        appContext ?: return
        sslSocketFactory = createSslSocketFactory(SelfSignedSslHelper.effectiveTrustManager())
        sslSocketFactory?.let {
            // ExoPlayer uses HttpsURLConnection in DefaultHttpDataSource.
            HttpsURLConnection.setDefaultSSLSocketFactory(it)
        }
        // TLS policy changed: cached HTTP clients must not reuse connections established under
        // the previous policy.
        SelfSignedSslHelper.notifyTlsPolicyChanged()
    }

    @JvmStatic
    fun createSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory? {
        val context = appContext ?: return null
        return try {
            val clientKeyManager = object : X509KeyManager {
                override fun getClientAliases(keyType: String?, issuers: Array<Principal>?) = null

                override fun chooseClientAlias(
                    keyType: Array<String>?,
                    issuers: Array<Principal>?,
                    socket: Socket?
                ): String? {
                    val clientCert = Preferences.getClientCert() ?: return null
                    val server = Preferences.getServer() ?: return null
                    return if (server.toUri().host == socket?.inetAddress?.hostName) {
                        clientCert
                    } else null
                }

                override fun getServerAliases(keyType: String?, issuers: Array<Principal>?) = null

                override fun chooseServerAlias(
                    keyType: String?,
                    issuers: Array<Principal>?,
                    socket: Socket?
                ) = null

                override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
                    val clientCert = Preferences.getClientCert()
                    return if (alias == clientCert && clientCert != null) {
                        KeyChain.getCertificateChain(context, clientCert)
                    } else null
                }

                override fun getPrivateKey(alias: String?): PrivateKey? {
                    val clientCert = Preferences.getClientCert()
                    return if (alias == clientCert && clientCert != null) {
                        KeyChain.getPrivateKey(context, clientCert)
                    } else null
                }
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(arrayOf(clientKeyManager), arrayOf<TrustManager>(trustManager), SecureRandom())
            sslContext.socketFactory
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Failed setting mTLS", e)
            null
        } catch (e: KeyManagementException) {
            Log.e(TAG, "Failed setting mTLS", e)
            null
        }
    }
}
