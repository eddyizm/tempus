package com.eddyizm.tempus.lan

import java.net.Socket
import java.security.Principal
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509KeyManager

/** Restricts the platform's compatible alias selection to the LAN identity. */
internal class LanIdentityKeyManager(private val alias: String, private val delegate: X509KeyManager) : X509ExtendedKeyManager() {
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        delegate.getClientAliases(keyType, issuers)?.filter { it == alias }?.takeIf { it.isNotEmpty() }?.toTypedArray()
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        delegate.getServerAliases(keyType, issuers)?.filter { it == alias }?.takeIf { it.isNotEmpty() }?.toTypedArray()
    override fun chooseClientAlias(keyTypes: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? =
        keyTypes?.firstNotNullOfOrNull { getClientAliases(it, issuers)?.firstOrNull() }
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? =
        getServerAliases(keyType, issuers)?.firstOrNull()
    override fun chooseEngineClientAlias(keyTypes: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        chooseClientAlias(keyTypes, issuers, null)
    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        chooseServerAlias(keyType, issuers, null)
    override fun getCertificateChain(requested: String?) = if (requested == alias) delegate.getCertificateChain(alias) else null
    override fun getPrivateKey(requested: String?) = if (requested == alias) delegate.getPrivateKey(alias) else null
}
