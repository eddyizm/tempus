package com.eddyizm.tempus.upnp

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlin.concurrent.thread

/** Takes a renderer's event NOTIFY requests and hands the Master volume in each to [onVolume], on its own thread. */
class UpnpEvents private constructor(
    private val server: ServerSocket,
    private val renderer: InetAddress,
    private val onVolume: (Int) -> Unit
) {
    val callbackUrl = "http://${server.inetAddress.hostAddress}:${server.localPort}/"

    init {
        thread(name = "UpnpEvents", isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                try {
                    socket.use { answer(it) }
                } catch (e: Exception) {
                    Log.d(TAG, "could not close an event connection", e)
                }
            }
        }
    }

    fun close() {
        try {
            server.close()
        } catch (e: Exception) {
            Log.d(TAG, "event listener did not close", e)
        }
    }

    private fun answer(socket: Socket) {
        // Anyone on the network can reach this port, and a forged level would be stepped from.
        // Checked before reading, so nobody else can hold this thread by sending slowly.
        if (socket.inetAddress != renderer) {
            Log.w(TAG, "ignoring an event from ${socket.inetAddress}, the renderer is $renderer")
            return
        }
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = socket.getInputStream()
            val head = readHead(input)
            fun header(name: String) =
                head.firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
            // UDA 1.1 allows a chunked NOTIFY, and its own example quotes the value.
            val body = if (header("Transfer-Encoding")?.contains("chunked", ignoreCase = true) == true) {
                readChunked(input)
            } else {
                readBody(input, header("Content-Length")?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0)
            }
            socket.getOutputStream().write(OK)
            UpnpControlPoint.masterVolumeOf(String(body, Charsets.UTF_8))?.let(onVolume)
        } catch (e: Exception) {
            Log.d(TAG, "could not read an event", e)
        }
    }

    private fun readBody(input: InputStream, length: Int): ByteArray {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        return body.copyOf(read)
    }

    private fun readChunked(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val size = readLine(input)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: return out.toByteArray()
            if (size == 0) break
            if (size < 0 || size > MAX_BODY_BYTES - out.size()) return out.toByteArray()
            out.write(readBody(input, size))
            readLine(input)
        }
        // Trailers and the closing blank line.
        readHead(input)
        return out.toByteArray()
    }

    private fun readHead(input: InputStream): List<String> =
        generateSequence { readLine(input)?.takeIf { it.isNotEmpty() } }.take(MAX_HEAD_LINES).toList()

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (line.length < MAX_LINE_BYTES) {
            val c = input.read()
            if (c < 0) return null
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            line.append(c.toChar())
        }
        return null
    }

    companion object {
        private const val TAG = "UpnpEvents"

        private const val READ_TIMEOUT_MS = 2000
        private const val MAX_LINE_BYTES = 8 * 1024
        private const val MAX_HEAD_LINES = 64
        private const val MAX_BODY_BYTES = 64 * 1024

        private val OK = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()

        /** Blocks. Listens on the address this phone uses to reach the renderer. */
        fun open(eventUrl: String, onVolume: (Int) -> Unit): UpnpEvents {
            val uri = URI(eventUrl)
            val renderer = InetAddress.getByName(uri.host)
            val local = DatagramSocket().use {
                it.connect(InetSocketAddress(renderer, if (uri.port > 0) uri.port else 80))
                it.localAddress
            }
            return UpnpEvents(ServerSocket(0, 0, local), renderer, onVolume)
        }
    }
}
