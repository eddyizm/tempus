package com.cappielloantonio.tempo.util

import android.content.Context
import android.security.KeyChain
import android.util.Log
import androidx.core.net.toUri
import okhttp3.internal.platform.Platform
import java.net.Socket
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509KeyManager

object ClientCertManager {

    private const val TAG = "ClientCertManager"

    val trustManager = Platform.get().platformTrustManager()
    var sslSocketFactory: SSLSocketFactory? = null
        private set

    @JvmStatic
    fun setupSslSocketFactory(context: Context) {
        sslSocketFactory = createSslSocketFactory(context)
        sslSocketFactory?.let {
            // HttpsURLConnection is used both by:
            // - Glide: in IPv6StringLoader
            // - ExoPlayer: in DefaultHttpDataSource
            HttpsURLConnection.setDefaultSSLSocketFactory(it)
        }
    }

    private fun createSslSocketFactory(context: Context): SSLSocketFactory? {
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
                        KeyChain.getCertificateChain(
                            context,
                            clientCert
                        )
                    } else null
                }

                override fun getPrivateKey(alias: String?): PrivateKey? {
                    val clientCert = Preferences.getClientCert()
                    return if (alias == clientCert && clientCert != null) {
                        KeyChain.getPrivateKey(
                            context,
                            clientCert
                        )
                    } else null
                }
            }

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(arrayOf(clientKeyManager), arrayOf(trustManager), null)
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