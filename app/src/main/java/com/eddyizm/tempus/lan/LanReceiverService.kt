package com.eddyizm.tempus.lan

import android.app.*
import android.content.*
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.repository.QueueRepository
import com.eddyizm.tempus.service.MediaService
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.util.MappingUtil
import com.eddyizm.tempus.util.Preferences
import org.json.JSONObject
import org.json.JSONArray
import androidx.media3.common.Player
import java.net.Socket
import java.util.concurrent.*
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@androidx.annotation.OptIn(UnstableApi::class)
class LanReceiverService : Service() {
    data class PairRequest(val fingerprint: String, val name: String, val code: String, val time: Long)
    companion object {
        const val PREFS = "lan-playback"
        @Volatile var running = false
        @Volatile var error: String? = null
        @Volatile var pairingUntil = 0L
        val pending = ConcurrentHashMap<String, PairRequest>()
        fun prefs(context: Context) = context.getSharedPreferences(PREFS, MODE_PRIVATE)
        fun name(context: Context) = prefs(context).getString("name", Build.MODEL)!!.take(40)
        fun trusted(context: Context) = prefs(context).getStringSet("trusted", emptySet())!!.toSet()
        fun openPairing() {
            if (!running) return
            pending.clear()
            pairingUntil = SystemClock.elapsedRealtime() + 120000
        }

        fun closePairing() {
            pairingUntil = 0L
            pending.clear()
        }

        fun approve(context: Context, request: PairRequest): Boolean {
            // A confirmation dialog may outlive the switch or the deadline. Check
            // authorization here as well, so stale UI cannot reopen pairing.
            if (!running || !LanPolicy.mayApprove(SystemClock.elapsedRealtime(), pairingUntil, request.time) ||
                pending[request.fingerprint] != request) return false
            prefs(context).edit().putStringSet("trusted", trusted(context) + request.fingerprint)
                .putString("peer-${request.fingerprint}", request.name).apply()
            pending.remove(request.fingerprint)
            LanDiagnostics.record("Receiver approved controller")
            return true
        }
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var alive = true
    private val workers = ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
    private val watchers = Semaphore(2)
    private var server: SSLServerSocket? = null
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private lateinit var nsd: NsdManager
    private var registered = false
    private lateinit var browserFuture: com.google.common.util.concurrent.ListenableFuture<MediaBrowser>
    private var serverIdentity: String? = null

    private val registration = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            registered = true
            LanDiagnostics.record("Receiver advertised on local network")
            if (!alive) runCatching { nsd.unregisterService(this) }
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) { registered = false }
        override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { fail(getString(R.string.lan_discovery_error)) }
        override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) { registered = false }
    }

    override fun onCreate() {
        super.onCreate()
        error = null
        LanDiagnostics.record("Receiver starting")
        serverIdentity = Preferences.getServer()
        val notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(
            NotificationChannel("lan_receiver", getString(R.string.lan_title), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, LanPlaybackActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, LanReceiverService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(7301, NotificationCompat.Builder(this, "lan_receiver")
            .setSmallIcon(R.drawable.ic_queue).setContentTitle(getString(R.string.lan_available))
            .setContentText(name(this)).setContentIntent(open).setOngoing(true)
            .addAction(0, getString(R.string.lan_disable), stop).build())
        nsd = getSystemService(NsdManager::class.java)
        browserFuture = MediaBrowser.Builder(this, SessionToken(this, ComponentName(this, MediaService::class.java))).buildAsync()
        running = true
        Thread({
            try {
                val listener = LanWire.context().serverSocketFactory.createServerSocket(0) as SSLServerSocket
                listener.needClientAuth = true
                server = listener
                if (!alive) { listener.close(); return@Thread }
                nsd.registerService(NsdServiceInfo().apply {
                    serviceName = "Tempus ${name(this@LanReceiverService)}"
                    serviceType = LanWire.TYPE
                    port = listener.localPort
                }, NsdManager.PROTOCOL_DNS_SD, registration)
                while (alive) {
                    val socket = listener.accept() as SSLSocket
                    socket.soTimeout = 15000
                    sockets.add(socket)
                    try { workers.execute { serve(socket) } }
                    catch (_: RejectedExecutionException) { sockets.remove(socket); socket.close() }
                }
            } catch (e: Exception) {
                LanDiagnostics.record("Receiver listener failed", e)
                if (alive) fail(getString(R.string.lan_connection_error))
            }
        }, "tempus-lan-listener").start()
    }

    private fun fail(message: String) { error = message; main.post { stopSelf() } }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") stopSelf()
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?) = null

    private fun serve(socket: SSLSocket) {
        var handshakeComplete = false
        try {
            LanDiagnostics.record("Receiver accepted connection; starting TLS")
            socket.startHandshake()
            handshakeComplete = true
            LanDiagnostics.record("Receiver TLS established")
            error = null
            val fingerprint = LanWire.peer(socket)
            require(fingerprint != LanWire.own(socket))
            val request = LanWire.read(socket)
            val command = request.optString("command")
            if (!trusted(this).contains(fingerprint)) {
                LanDiagnostics.record("Receiver unpaired request; windowOpen=${SystemClock.elapsedRealtime() < pairingUntil}")
                require(LanPolicy.mayPair(command, SystemClock.elapsedRealtime(), pairingUntil))
                pending.entries.removeAll { SystemClock.elapsedRealtime() - it.value.time > 120000 }
                require(pending.size < 4 || pending.containsKey(fingerprint))
                val code = LanWire.code(fingerprint, LanWire.own(socket))
                pending[fingerprint] = PairRequest(fingerprint, request.optString("name").take(40), code, SystemClock.elapsedRealtime())
                LanWire.write(socket, JSONObject().put("pair", code))
                LanDiagnostics.record("Receiver pairing code sent; awaiting approval")
                return
            }
            require(Preferences.getServer() == serverIdentity && !serverIdentity.isNullOrEmpty())
            if (command == "watch") {
                require(watchers.tryAcquire())
                try { watch(socket, fingerprint) } finally { watchers.release() }
                return
            }
            val response = if (command == "queue") loadQueue(request, fingerprint) else if (command == "edit") editQueue(request, fingerprint) else onMain {
                require(alive && trusted(this).contains(fingerprint))
                val player = browserFuture.get()
                when (command) {
                    "hello", "status" -> Unit
                    "play" -> player.play()
                    "pause" -> player.pause()
                    "next" -> player.seekToNextMediaItem()
                    "previous" -> player.seekToPrevious()
                    "shuffle" -> player.shuffleModeEnabled = request.getBoolean("enabled")
                    "repeat" -> player.repeatMode = request.getInt("mode").also { require(it in 0..2) }
                    "speed" -> {
                        val speed = request.getDouble("speed").toFloat()
                        val pitch = request.getDouble("pitch").toFloat()
                        require(speed.isFinite() && pitch.isFinite() && speed in 0.1f..5f && pitch in 0.1f..5f)
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed, pitch)
                    }
                    "timer" -> {
                        val timer = com.eddyizm.tempus.util.SleepTimerManager.getInstance()
                        when (request.getString("mode")) {
                            "cancel" -> timer.cancelTimer()
                            "end" -> timer.startEndOfTrack()
                            "minutes" -> timer.startTimer(request.getInt("minutes").also { require(it in 1..1440) })
                            else -> error("Unknown timer mode")
                        }
                    }
                    "seek" -> player.seekTo(request.getLong("position").coerceIn(0, player.duration.coerceAtLeast(0)))
                    "queue_state" -> return@onMain queuePage(player, request.optInt("offset", 0))
                    "volume" -> player.volume = request.getDouble("value").toFloat().coerceIn(0f, 1f)
                    "device_volume" -> {
                        val audio = getSystemService(android.media.AudioManager::class.java)
                        val music = android.media.AudioManager.STREAM_MUSIC
                        if (request.has("direction")) {
                            val direction = request.getInt("direction")
                            require(direction == android.media.AudioManager.ADJUST_RAISE || direction == android.media.AudioManager.ADJUST_LOWER)
                            audio.adjustStreamVolume(music, direction, 0)
                        } else {
                            val level = request.getInt("level").coerceIn(0, audio.getStreamMaxVolume(music))
                            audio.setStreamVolume(music, level, 0)
                        }
                    }
                    else -> error("Unknown command")
                }
                snapshot(player)
            }
            LanWire.write(socket, response)
        } catch (e: Exception) {
            LanDiagnostics.record("Receiver failed at ${if (handshakeComplete) "request" else "TLS"}", e)
            if (!handshakeComplete && alive) error = getString(R.string.lan_tls_error)
            // Never expose server URLs, credentials or exception messages over this protocol.
            // Send the error before closing the socket, and only after TLS succeeded.
            if (handshakeComplete) runCatching { LanWire.write(socket, JSONObject().put("error", "unavailable")) }
        } finally { sockets.remove(socket); runCatching { socket.close() } }
    }

    private fun snapshot(player: Player): JSONObject {
        val media = player.mediaMetadata
        val audio = getSystemService(android.media.AudioManager::class.java)
        return JSONObject().put("ok", true).put("remoteVersion", 4)
            .put("server", LanWire.fingerprint(serverIdentity!!.trimEnd('/').toByteArray()))
            .put("name", name(this)).put("title", media.title?.toString()?.take(256) ?: "")
            .put("artist", media.artist?.toString()?.take(256) ?: "")
            .put("album", media.albumTitle?.toString()?.take(256) ?: "")
            .put("cover", media.extras?.getString("coverArtId")?.take(512) ?: "")
            .put("position", player.currentPosition.coerceAtLeast(0))
            .put("duration", player.duration.coerceAtLeast(0))
            .put("playing", player.isPlaying).put("playWhenReady", player.playWhenReady)
            .put("volume", player.volume).put("index", player.currentMediaItemIndex)
            .put("deviceVolume", audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
            .put("deviceVolumeMax", audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC))
            .put("shuffle", player.shuffleModeEnabled).put("repeat", player.repeatMode)
            .put("id", player.currentMediaItem?.mediaId ?: "")
            .put("speed", player.playbackParameters.speed).put("pitch", player.playbackParameters.pitch)
            .put("timerActive", com.eddyizm.tempus.util.SleepTimerManager.getInstance().isActive)
            .put("timerEnd", com.eddyizm.tempus.util.SleepTimerManager.getInstance().isEndOfTrack)
            .put("timerRemaining", com.eddyizm.tempus.util.SleepTimerManager.getInstance().remainingFormatted)
            .put("next", player.hasNextMediaItem()).put("previous", player.hasPreviousMediaItem())
            .put("count", player.mediaItemCount).put("revision", queueRevision(player))
    }

    private fun queuePage(player: Player, offset: Int): JSONObject {
        require(offset in 0..player.mediaItemCount)
        val items = JSONArray()
        for (i in offset until (offset + minOf(20, player.mediaItemCount - offset))) {
            val metadata = player.getMediaItemAt(i).mediaMetadata
            items.put(JSONObject().put("id", player.getMediaItemAt(i).mediaId)
                .put("album", metadata.albumTitle?.toString()?.take(256) ?: "")
                .put("cover", metadata.extras?.getString("coverArtId")?.take(512) ?: "")
                .put("albumId", metadata.extras?.getString("albumId")?.take(512) ?: "")
                .put("artistId", metadata.extras?.getString("artistId")?.take(512) ?: "")
                .put("suffix", metadata.extras?.getString("suffix")?.take(32) ?: "")
                .put("bitrate", metadata.extras?.getInt("bitrate") ?: 0)
                .put("duration", metadata.extras?.getInt("duration") ?: 0).put("title", metadata.title?.toString()?.take(256) ?: "")
                .put("artist", metadata.artist?.toString()?.take(256) ?: ""))
        }
        return JSONObject().put("ok", true).put("items", items).put("index", player.currentMediaItemIndex)
            .put("count", player.mediaItemCount).put("revision", queueRevision(player))
    }

    private fun watch(socket: SSLSocket, fingerprint: String) {
        val changes = ArrayBlockingQueue<Boolean>(1)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { changes.offer(true) }
        }
        onMain { browserFuture.get().addListener(listener) }
        LanDiagnostics.record("Receiver live updates started")
        try {
            while (alive && !Thread.currentThread().isInterrupted) {
                val state = onMain {
                    require(alive && trusted(this).contains(fingerprint) && Preferences.getServer() == serverIdentity)
                    snapshot(browserFuture.get())
                }
                LanWire.write(socket, state)
                changes.poll(1, TimeUnit.SECONDS)
            }
        } finally {
            main.post { if (browserFuture.isDone) runCatching { browserFuture.get().removeListener(listener) } }
            LanDiagnostics.record("Receiver live updates stopped")
        }
    }

    @Synchronized private fun loadQueue(request: JSONObject, fingerprint: String): JSONObject {
        require(request.getString("server") == LanWire.fingerprint(serverIdentity!!.trimEnd('/').toByteArray()))
        val ids = request.getJSONArray("ids")
        require(ids.length() in 1..500)
        val index = request.getInt("index")
        require(index in 0 until ids.length())
        val position = request.getLong("position").coerceAtLeast(0)
        LanPolicy.validateQueue((0 until ids.length()).map { ids.getString(it) }, index, position)
        // Resolve IDs with this phone's own Subsonic credentials. No stream URL is accepted.
        val deadline = SystemClock.elapsedRealtime() + 90000
        val songs = (0 until ids.length()).map { i ->
            require(alive && !Thread.currentThread().isInterrupted && SystemClock.elapsedRealtime() < deadline)
            require(trusted(this).contains(fingerprint) && Preferences.getServer() == serverIdentity)
            val id = ids.getString(i)
            require(id.length in 1..512)
            val call = App.getSubsonicClientInstance(false).browsingClient.getSong(id)
            call.timeout().timeout(10, TimeUnit.SECONDS)
            val response = call.execute()
            require(response.isSuccessful)
            requireNotNull(response.body()?.subsonicResponse?.song)
        }
        val items = songs.map { MappingUtil.mapMediaItem(it) }
        onMain {
            require(alive && trusted(this).contains(fingerprint) && Preferences.getServer() == serverIdentity)
            val player = browserFuture.get()
            player.setMediaItems(items, index, position)
            player.prepare()
            player.play()
        }
        QueueRepository().insertAll(songs, true, 0)
        return JSONObject().put("ok", true)
    }

    private fun queueRevision(player: Player): String = LanWire.fingerprint(
        (0 until player.mediaItemCount).joinToString("\u0000") { player.getMediaItemAt(it).mediaId }.toByteArray())

    @Synchronized private fun editQueue(request: JSONObject, fingerprint: String): JSONObject {
        require(request.getString("server") == LanWire.fingerprint(serverIdentity!!.trimEnd('/').toByteArray()))
        val action = request.getString("action")
        val authorized = {
            require(alive && trusted(this).contains(fingerprint) && Preferences.getServer() == serverIdentity)
        }
        val added = if (action in setOf("replace", "append", "next")) {
            val json = request.getJSONArray("ids")
            val ids = (0 until json.length()).map { json.getString(it) }
            LanQueuePolicy.validateSongs(ids)
            if (action == "replace") require(request.getInt("index") in ids.indices)
            val deadline = SystemClock.elapsedRealtime() + 90000
            ids.map { id ->
                authorized()
                require(!Thread.currentThread().isInterrupted && SystemClock.elapsedRealtime() < deadline)
                val call = App.getSubsonicClientInstance(false).browsingClient.getSong(id)
                call.timeout().timeout(10, TimeUnit.SECONDS)
                val response = call.execute()
                require(response.isSuccessful)
                requireNotNull(response.body()?.subsonicResponse?.song)
            }.map { MappingUtil.mapMediaItem(it) }
        } else emptyList()
        return onMain {
            authorized()
            val player = browserFuture.get()
            if (action !in setOf("replace", "append", "next") && !LanQueuePolicy.matchesRevision(request.optString("revision"), queueRevision(player))) {
                return@onMain JSONObject().put("error", "queue_changed")
            }
            when (action) {
                "replace" -> {
                    val index = request.getInt("index")
                    require(index in added.indices)
                    player.setMediaItems(added, index, 0)
                    player.prepare()
                    player.play()
                }
                "append", "next" -> {
                    LanQueuePolicy.validateInsertion(player.mediaItemCount, added.size)
                    val empty = player.mediaItemCount == 0
                    if (action == "next" && !empty) {
                        val index = player.currentMediaItemIndex + 1
                        MediaManager.requestPlayNextFixup(browserFuture.get(), index, added.size, player.mediaItemCount + added.size)
                        player.addMediaItems(index, added)
                    } else player.addMediaItems(added)
                    if (empty) player.prepare()
                }
                "select" -> {
                    val index = request.getInt("index")
                    require(index in 0 until player.mediaItemCount)
                    player.seekTo(index, 0)
                    player.prepare()
                    player.play()
                }
                "remove" -> {
                    val from = request.getInt("from")
                    val to = request.getInt("to")
                    LanQueuePolicy.validateRange(player.mediaItemCount, from, to)
                    player.removeMediaItems(from, to)
                }
                "move" -> {
                    val from = request.getInt("from")
                    val to = request.getInt("to")
                    LanQueuePolicy.validateMove(player.mediaItemCount, from, to)
                    player.moveMediaItem(from, to)
                }
                "reorder" -> {
                    val ids = request.getJSONArray("ids")
                    val ordered = (0 until ids.length()).map { ids.getString(it) }
                    val original = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
                    LanQueuePolicy.validateReorder(original, ordered)
                    ordered.forEachIndexed { destination, id ->
                        val source = (destination until player.mediaItemCount).first { player.getMediaItemAt(it).mediaId == id }
                        if (source != destination) player.moveMediaItem(source, destination)
                    }
                }
                "clear" -> player.clearMediaItems()
                else -> error("Unknown queue edit")
            }
            val songs = (0 until player.mediaItemCount).map { MappingUtil.mapToChild(player.getMediaItemAt(it)) }
            QueueRepository().insertAll(songs, true, 0)
            snapshot(player)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        require(browserFuture.isDone)
        val task = FutureTask(Callable { block() })
        main.post(task)
        return try { task.get(5, TimeUnit.SECONDS) } catch (e: Exception) { task.cancel(false); throw e }
    }

    override fun onDestroy() {
        LanDiagnostics.record("Receiver stopping")
        alive = false
        running = false
        closePairing()
        runCatching { server?.close() }
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
        if (registered) runCatching { nsd.unregisterService(registration) }
        MediaBrowser.releaseFuture(browserFuture)
        super.onDestroy()
    }
}
