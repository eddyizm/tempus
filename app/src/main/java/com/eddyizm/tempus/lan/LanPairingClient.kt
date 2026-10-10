package com.eddyizm.tempus.lan

import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import org.json.JSONObject
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket

/** The setup screen only pairs devices; playback belongs to LanRemoteSession. */
internal class LanPairingClient {
    data class Reply(val fingerprint: String, val code: String, val approved: Boolean)

    @Volatile private var socket: SSLSocket? = null

    @Suppress("DEPRECATION")
    fun hello(info: NsdServiceInfo, controllerName: String): Reply {
        var stage = LanConnectionFailure.Stage.IDENTITY
        val started = SystemClock.elapsedRealtime()
        try {
            val connection = LanWire.context().socketFactory.createSocket() as SSLSocket
            socket = connection
            return connection.use {
                connection.soTimeout = 15000
                stage = LanConnectionFailure.Stage.CONNECT
                connection.connect(InetSocketAddress(info.host, info.port), 5000)
                stage = LanConnectionFailure.Stage.TLS
                connection.startHandshake()
                stage = LanConnectionFailure.Stage.VERIFY
                val fingerprint = LanWire.peer(connection)
                val own = LanWire.own(connection)
                require(fingerprint != own)
                stage = LanConnectionFailure.Stage.RESPONSE
                LanWire.write(connection, JSONObject().put("command", "hello").put("name", controllerName))
                val response = LanWire.read(connection)
                require(!response.has("error"))
                val code = LanWire.code(fingerprint, own)
                // Derive the displayed code from TLS identities, never from peer text.
                require(response.optBoolean("ok") || response.optString("pair") == code)
                Reply(fingerprint, code, response.optBoolean("ok"))
            }
        } catch (failure: Exception) {
            // Exception messages may contain private addresses, so record only types.
            LanDiagnostics.record("Controller pairing failed at $stage (${SystemClock.elapsedRealtime() - started}ms)", failure)
            throw LanConnectionFailure(stage, failure)
        } finally {
            socket = null
        }
    }

    fun cancel() {
        runCatching { socket?.close() }
    }
}
