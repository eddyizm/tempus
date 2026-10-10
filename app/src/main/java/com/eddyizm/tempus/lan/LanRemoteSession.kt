package com.eddyizm.tempus.lan

import android.content.ComponentName
import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.eddyizm.tempus.R
import com.eddyizm.tempus.service.MediaService
import com.eddyizm.tempus.util.Preferences
import org.json.JSONObject
import org.json.JSONArray
import com.eddyizm.tempus.subsonic.models.Child
import android.widget.Toast
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/** Process-local selection, shared by setup, full player and mini player. No Activity references. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object LanRemoteSession {
    private val main = Handler(Looper.getMainLooper())
    private val updates = MutableLiveData(LanRemoteState())
    private val queueUpdates = MutableLiveData<List<Child>>(emptyList())
    private var loadedRevision: String? = null
    private var loadingQueue = false
    private var appContext: Context? = null
    private val watcher = Executors.newSingleThreadExecutor()
    private val commands = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0L
    @Volatile private var target: NsdServiceInfo? = null
    @Volatile private var pin: String? = null
    @Volatile private var server: String? = null
    @Volatile private var stream: SSLSocket? = null
    @Volatile private var commandSocket: SSLSocket? = null
    private var task: Future<*>? = null

    @JvmStatic fun state(): LiveData<LanRemoteState> = updates
    @JvmStatic fun current(): LanRemoteState = updates.value ?: LanRemoteState()
    @JvmStatic fun isActive(): Boolean = current().active
    @JvmStatic fun queueState(): LiveData<List<Child>> = queueUpdates
    @JvmStatic fun queueItems(): List<Child> = queueUpdates.value ?: emptyList()
    @JvmStatic fun queueIsCurrent(): Boolean = loadedRevision == current().revision
    @JvmStatic fun currentSong(): Child {
        val state = current()
        val items = queueItems()
        return if (queueIsCurrent() && state.index in items.indices) items[state.index]
        else Child(state.mediaId, title = state.title, artist = state.artist, album = state.album,
            coverArtId = state.coverId, duration = (state.duration / 1000).toInt())
    }

    @JvmStatic fun disconnect() {
        check(Looper.myLooper() == Looper.getMainLooper())
        LanRemoteVolume.release()
        generation++
        target = null; pin = null
        runCatching { stream?.close() }; stream = null
        runCatching { commandSocket?.close() }; commandSocket = null
        task?.cancel(true); task = null
        updates.value = LanRemoteState()
        queueUpdates.value = emptyList()
        loadedRevision = null; loadingQueue = false
        LanDiagnostics.record("Controller released remote playback; no stop command sent")
    }

    @JvmStatic @JvmOverloads fun connect(context: Context, info: NsdServiceInfo, expectedPin: String? = null) {
        disconnect()
        if (!Preferences.isLanRemoteEnabled()) return
        val token = generation
        val app = context.applicationContext
        appContext = app
        val localServer = Preferences.getServer() ?: return
        server = localServer
        target = info
        updates.value = LanRemoteState(active = true, name = info.serviceName)
        task = watcher.submit {
            var approved: String? = expectedPin
            var first = true
            try {
                val tls = LanWire.context()
                while (generation == token && !Thread.currentThread().isInterrupted) {
                    try {
                        if (Preferences.getServer() != localServer) throw ServerMismatch()
                        val socket = open(tls, info, token, true)
                        socket.use {
                            val peer = LanWire.peer(it)
                            val saved = LanReceiverService.prefs(app).getStringSet("receivers", emptySet())!!
                            if (peer !in saved || (approved != null && peer != approved)) throw PairingRequired()
                            approved = peer
                            pin = peer
                            LanWire.write(it, JSONObject().put("command", "watch"))
                            LanDiagnostics.record("Controller subscribed to live playback")
                            while (generation == token) {
                                val json = LanWire.read(it)
                                if (Preferences.getServer() != localServer) throw ServerMismatch()
                                if (json.has("error")) throw RequestRejected()
                                if (json.optInt("remoteVersion") < 4) throw UpdateRequired()
                                if (json.optString("server") != LanWire.fingerprint(localServer.trimEnd('/').toByteArray())) throw ServerMismatch()
                                val state = decode(json)
                                val pause = first
                                first = false
                                main.post {
                                    if (generation == token) {
                                        updates.value = state
                                        LanRemoteVolume.update(app, state)
                                        refreshQueue()
                                        if (pause) pauseLocal(app, token)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (generation != token || Thread.currentThread().isInterrupted) break
                        if (e is PairingRequired || e is ServerMismatch || e is UpdateRequired || e is RequestRejected) throw e
                        LanDiagnostics.record("Controller live connection interrupted", e)
                        main.post { if (generation == token) {
                            updates.value = current().copy(connected = false, error = R.string.lan_reconnecting)
                            LanRemoteVolume.release()
                        } }
                        Thread.sleep(2000)
                    }
                }
            } catch (e: Exception) {
                if (generation == token) {
                    LanDiagnostics.record("Controller connection rejected", e)
                    main.post { if (generation == token) {
                        LanRemoteVolume.release()
                        updates.value = current().copy(connected = false, error = when (e) {
                        is PairingRequired -> R.string.lan_pair_first
                        is ServerMismatch -> R.string.lan_server_mismatch
                        is UpdateRequired -> R.string.lan_update_both
                        else -> R.string.lan_connection_error
                    }) } }
                }
            } finally { if (generation == token) stream = null }
        }
    }

    private fun decode(json: JSONObject) = LanRemoteState(
        active = true, connected = true, name = json.optString("name"), title = json.optString("title"),
        artist = json.optString("artist"), album = json.optString("album"), coverId = json.optString("cover"),
        position = json.optLong("position").coerceAtLeast(0), duration = json.optLong("duration").coerceAtLeast(0),
        playing = json.optBoolean("playing"), playWhenReady = json.optBoolean("playWhenReady"),
        volume = json.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f), index = json.optInt("index", -1),
        deviceVolume = json.optInt("deviceVolume"), deviceVolumeMax = json.optInt("deviceVolumeMax"),
        count = json.optInt("count"), receivedAt = SystemClock.elapsedRealtime(), revision = json.optString("revision"),
        shuffle = json.optBoolean("shuffle"), repeat = json.optInt("repeat"),
        hasNext = json.optBoolean("next"), hasPrevious = json.optBoolean("previous"),
        mediaId = json.optString("id"), speed = json.optDouble("speed", 1.0).toFloat(),
        pitch = json.optDouble("pitch", 1.0).toFloat(), timerActive = json.optBoolean("timerActive"),
        timerEndOfTrack = json.optBoolean("timerEnd"), timerRemaining = json.optString("timerRemaining"))

    private fun pauseLocal(context: Context, token: Long) {
        val future = MediaBrowser.Builder(context, SessionToken(context, ComponentName(context, MediaService::class.java))).buildAsync()
        future.addListener({
            try { if (generation == token && isActive()) future.get().pause() }
            catch (e: Exception) { LanDiagnostics.record("Controller could not pause local playback", e) }
            finally { MediaBrowser.releaseFuture(future) }
        }, { main.post(it) })
    }

    private fun open(tls: SSLContext, info: NsdServiceInfo, token: Long, watching: Boolean): SSLSocket {
        val socket = tls.socketFactory.createSocket() as SSLSocket
        if (watching) stream = socket else commandSocket = socket
        try {
            check(generation == token)
            socket.soTimeout = 5000
            @Suppress("DEPRECATION")
            socket.connect(InetSocketAddress(info.host, info.port), 5000)
            socket.startHandshake()
            check(generation == token)
            return socket
        } catch (e: Exception) { socket.close(); throw e }
    }

    @JvmStatic @JvmOverloads fun command(name: String, value: Long = 0) {
        if (!current().connected) return
        val body = JSONObject().put("command", name)
        if (name == "seek") body.put("position", value)
        if (name == "volume") body.put("value", value.coerceIn(0, 100) / 100.0)
        if (name == "shuffle") body.put("enabled", value != 0L)
        if (name == "repeat") body.put("mode", value.coerceIn(0, 2))
        request(body) { }
    }

    fun adjustDeviceVolume(direction: Int) {
        if (!current().connected || direction !in listOf(android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.ADJUST_LOWER)) return
        request(JSONObject().put("command", "device_volume").put("direction", direction)) { json ->
            val state = current().copy(deviceVolume = json.optInt("deviceVolume"), deviceVolumeMax = json.optInt("deviceVolumeMax"))
            updates.value = state
            appContext?.let { LanRemoteVolume.update(it, state) }
        }
    }

    fun setDeviceVolume(volume: Int) {
        if (!current().connected) return
        request(JSONObject().put("command", "device_volume").put("level", volume.coerceIn(0, current().deviceVolumeMax))) { json ->
            val state = current().copy(deviceVolume = json.optInt("deviceVolume"), deviceVolumeMax = json.optInt("deviceVolumeMax"))
            updates.value = state
            appContext?.let { LanRemoteVolume.update(it, state) }
        }
    }

    @JvmStatic fun playbackParameters(speed: Float, pitch: Float) {
        if (!current().connected) return
        request(JSONObject().put("command", "speed").put("speed", speed).put("pitch", pitch)) { }
    }

    @JvmStatic fun sleepTimer(mode: String, minutes: Int) {
        if (!current().connected) return
        request(JSONObject().put("command", "timer").put("mode", mode).put("minutes", minutes)) { }
    }

    private fun request(body: JSONObject, failure: () -> Unit = {}, success: (JSONObject) -> Unit) {
        val token = generation
        val info = target ?: return
        val expected = pin ?: return
        commands.execute {
            try {
                check(generation == token && Preferences.getServer() == server)
                val result = open(LanWire.context(), info, token, false).use {
                    if (body.optString("command") == "edit") it.soTimeout = 120000
                    require(LanWire.peer(it) == expected)
                    check(generation == token && Preferences.getServer() == server)
                    LanWire.write(it, body)
                    LanWire.read(it).also { response ->
                        if (response.optString("error") == "queue_changed") throw QueueChanged()
                        require(response.optBoolean("ok"))
                    }
                }
                main.post { if (generation == token) success(result) }
            } catch (e: Exception) {
                LanDiagnostics.record("Remote command failed", e)
                main.post { if (generation == token) {
                    val error = if (e is QueueChanged) R.string.lan_queue_changed else R.string.lan_connection_error
                    updates.value = current().copy(error = error)
                    if (body.optString("command") != "queue_state") notify(error)
                    failure()
                } }
            } finally { if (generation == token) commandSocket = null }
        }
    }

    private fun refreshQueue() {
        if (!current().connected || loadingQueue || current().revision == loadedRevision) return
        if (current().count > LanQueuePolicy.LIMIT) {
            if (loadedRevision != current().revision) notify(R.string.lan_queue_limit)
            loadedRevision = current().revision
            queueUpdates.value = emptyList()
            return
        }
        loadingQueue = true
        val rows = mutableListOf<Child>()
        var revision: String? = null
        fun page(offset: Int) {
            request(JSONObject().put("command", "queue_state").put("offset", offset), { loadingQueue = false }) { json ->
                if (revision != null && revision != json.optString("revision")) {
                    updates.value = current().copy(error = R.string.lan_queue_changed)
                    loadingQueue = false
                    return@request
                }
                revision = json.optString("revision")
                val items = json.getJSONArray("items")
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    rows.add(Child(item.getString("id"), title = item.optString("title"),
                        artist = item.optString("artist"), album = item.optString("album"),
                        coverArtId = item.optString("cover"), duration = item.optInt("duration"),
                        albumId = item.optString("albumId").takeIf { it.isNotBlank() },
                        artistId = item.optString("artistId").takeIf { it.isNotBlank() },
                        suffix = item.optString("suffix").takeIf { it.isNotBlank() }, bitrate = item.optInt("bitrate")))
                }
                if (items.length() > 0 && rows.size < minOf(json.optInt("count"), 500)) page(rows.size)
                else {
                    loadingQueue = false
                    if (current().revision == revision) {
                        loadedRevision = revision
                        queueUpdates.value = rows.toList()
                    }
                }
            }
        }
        page(0)
    }

    private fun notify(message: Int) {
        main.post { appContext?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() } }
    }

    /** True means the action belongs to the selected receiver, even if it is unavailable. */
    @JvmStatic fun routeSongs(songs: List<Child>, action: String, index: Int): Boolean {
        return routeSongs(songs, action, index, 0L)
    }

    /** A replacement may start at a saved playback position. */
    @JvmStatic fun routeSongs(songs: List<Child>, action: String, index: Int, positionMs: Long): Boolean {
        if (!isActive()) return false
        if (!current().connected) { notify(R.string.lan_reconnecting); return true }
        if (action == "reorder" && loadedRevision != current().revision) {
            notify(R.string.lan_queue_changed); queueUpdates.value = queueItems().toList(); return true
        }
        val ids = songs.map { it.id }
        if (runCatching { LanQueuePolicy.validateSongs(ids) }.isFailure || (action == "replace" && index !in ids.indices)) {
            notify(R.string.lan_queue_limit); return true
        }
        val body = editBody(action).put("ids", JSONArray(ids)).put("index", index)
        if (action == "replace") body.put("position", positionMs.coerceAtLeast(0))
        if (body.toString().toByteArray(Charsets.UTF_8).size > LanWire.MAX_FRAME) { notify(R.string.lan_queue_limit); return true }
        if (action in setOf("replace", "append", "next")) notify(R.string.lan_transferring)
        request(body, { loadedRevision = null; refreshQueue() }) { loadedRevision = null; refreshQueue() }
        return true
    }

    private fun editBody(action: String) = JSONObject().put("command", "edit").put("action", action)
        .put("server", LanWire.fingerprint((server ?: "").trimEnd('/').toByteArray()))
        .put("revision", loadedRevision ?: current().revision)

    @JvmStatic fun routeEdit(action: String, from: Int, to: Int): Boolean {
        if (!isActive()) return false
        if (!current().connected) { notify(R.string.lan_reconnecting); return true }
        if (action != "clear" && loadedRevision != current().revision) {
            notify(R.string.lan_queue_changed); queueUpdates.value = queueItems().toList(); return true
        }
        request(editBody(action).put("from", from).put("to", to).put("index", from), { loadedRevision = null; refreshQueue() }) {
            loadedRevision = null; refreshQueue()
        }
        return true
    }

    @JvmStatic fun rejectUnsupported(): Boolean {
        if (!isActive()) return false
        notify(R.string.lan_music_only)
        return true
    }

    private class PairingRequired : Exception()
    private class ServerMismatch : Exception()
    private class UpdateRequired : Exception()
    private class RequestRejected : Exception()
    private class QueueChanged : Exception()
}
