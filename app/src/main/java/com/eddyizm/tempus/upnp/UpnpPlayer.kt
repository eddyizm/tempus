package com.eddyizm.tempus.upnp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.service.MediaManager
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.MusicUtil
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** media3 [Player] backed by a UPnP renderer, driven by a poll because remote state is not observable. */
@UnstableApi
class UpnpPlayer(
    private val controlPoint: UpnpControlPoint,
    val device: UpnpDevice,
    looper: Looper,
    private val worker: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
    private val streamUrl: (MediaItem) -> String? = { streamUrlFor(it) },
    context: Context? = null
) : SimpleBasePlayer(looper) {

    private val handler = Handler(looper)

    // Screen off, the phone suspends and the poll stops with it.
    private val wakeLock: PowerManager.WakeLock? =
        (context?.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }

    @Volatile private var playlist: List<Entry> = emptyList()
    @Volatile private var index = 0
    @Volatile private var playWhenReady = false
    @Volatile private var playbackState = Player.STATE_IDLE
    @Volatile private var positionMs = 0L
        set(value) {
            field = value
            positionIsCurrentNow()
        }
    @Volatile private var positionAt = 0L

    // Declared, since a position held below the length never lets media3 infer a track ending.
    @Volatile private var rendererAdvancedItself = false

    // A stop at the end of the track before this one is waited out, since a load sent then collides on an LG C1.
    @Volatile private var nextTrackSentFor: String? = null
    @Volatile private var stoppedAtEndPolls = 0

    @Volatile private var outsidePlayPausePolls = 0

    @Volatile private var seekSentAt = 0L

    // Furthest the bar got on the current track, which is what decides whether it counted.
    @Volatile private var playedHighWaterMs = 0L
    @Volatile private var durationUs = C.TIME_UNSET
    @Volatile private var released = false

    // Null once stopped. An LG C1 handed the URL it already plays wedges, so that restarts in place.
    @Volatile private var rendererUrl: String? = null

    // Loads asked for, so one overtaken before it ran does nothing and a burst of taps costs one.
    @Volatile private var loadGeneration = 0L

    // While it trails loadGeneration the renderer still describes the track before the tap.
    @Volatile private var loadedGeneration = 0L

    // A same track seek takes a generation without a load, so prepare cannot read generations alone.
    private val pendingLoads = AtomicInteger()

    // Sent after the next Play, since a Seek to a stopped LG C1 hangs it.
    @Volatile private var pendingSeekMs: Long? = null

    @Volatile private var error: PlaybackException? = null

    // A renderer reports stopped both before it starts and after it finishes.
    @Volatile private var rendererStarted = false
    @Volatile private var pollsBeforeStart = 0

    // Only reads that got nothing back count, since a fault is an answer.
    @Volatile private var failingSince = 0L

    // Checked by reads already queued, which would otherwise undo a stop.
    @Volatile private var polling = false
        set(value) {
            field = value
            updateWakeLock()
        }

    private class Entry(val uid: Long, val item: MediaItem, val url: String?) {
        val tracks: Tracks = audioTracks(uid, mimeTypeFor(item, url))
    }

    private var nextUid = 0L

    private fun entry(item: MediaItem) = Entry(nextUid++, item, streamUrl(item))

    private val poll = object : Runnable {
        override fun run() {
            if (released || !polling) return
            worker.execute { readRemoteState() }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun getState(): State {
        val currentUid = currentEntry()?.uid
        val items = playlist.map { entry ->
            MediaItemData.Builder(entry.uid)
                .setMediaItem(entry.item)
                .setIsSeekable(true)
                .setDurationUs(if (entry.uid == currentUid) durationUs else C.TIME_UNSET)
                .setTracks(entry.tracks)
                .build()
        }

        val position = if (playbackState == Player.STATE_READY && playWhenReady) {
            // Clamped on every read, since media3 takes a position at the length as the track ending.
            val from = positionNow()
            val at = SystemClock.elapsedRealtime()
            val ceiling = barCeiling()
            PositionSupplier { minOf(from + (SystemClock.elapsedRealtime() - at), ceiling) }
        } else {
            PositionSupplier.getConstant(positionMs)
        }

        val state = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayerError(error)
            .setPlaybackState(
                when {
                    error != null -> Player.STATE_IDLE
                    // Empty playlist is only legal idle or ended. The builder throws otherwise.
                    items.isEmpty() && playbackState != Player.STATE_IDLE -> Player.STATE_ENDED
                    else -> playbackState
                }
            )
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(index.coerceIn(0, maxOf(0, items.size - 1)))
            .setContentPositionMs(position)

        if (rendererAdvancedItself) {
            rendererAdvancedItself = false
            state.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, positionMs)
        }
        return state.build()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        playlist = mediaItems.map { entry(it) }
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        positionMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs
        durationUs = C.TIME_UNSET
        return loadLater(seekToMs = positionMs)
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> =
        editQueue { queue -> queue.addAll(index, mediaItems.map { entry(it) }) }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> =
        editQueue { queue -> queue.subList(fromIndex, toIndex).clear() }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> =
        editQueue { queue ->
            val moved = ArrayList(queue.subList(fromIndex, toIndex))
            queue.subList(fromIndex, toIndex).clear()
            queue.addAll(newIndex.coerceIn(0, queue.size), moved)
        }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> = editQueue { queue ->
        val replaced = ArrayList(queue.subList(fromIndex, toIndex))
        queue.subList(fromIndex, toIndex).clear()
        queue.addAll(
            fromIndex,
            mediaItems.mapIndexed { i, item -> Entry(replaced.getOrNull(i)?.uid ?: nextUid++, item, streamUrl(item)) }
        )
    }

    private fun editQueue(edit: (MutableList<Entry>) -> Unit): ListenableFuture<*> {
        val playingUid = currentEntry()?.uid
        val nextUrlBefore = urlOf(playlist.getOrNull(index + 1))

        val queue = playlist.toMutableList()
        edit(queue)
        playlist = queue

        val movedTo = queue.indexOfFirst { it.uid == playingUid }
        val currentGone = playingUid != null && movedTo < 0
        index = if (movedTo >= 0) movedTo else index.coerceIn(0, maxOf(0, queue.size - 1))

        invalidateLater()

        return when {
            queue.isEmpty() -> worker.submit<Unit> {
                stopRenderer("on an emptied queue")
                playbackState = Player.STATE_ENDED
                stopPolling()
                invalidateLater()
            }
            currentGone -> {
                positionMs = 0L
                durationUs = C.TIME_UNSET
                loadLater(seekToMs = 0L)
            }
            urlOf(queue.getOrNull(index + 1)) != nextUrlBefore ->
                worker.submit<Unit> { sendNextTrack() }
            else -> Futures.immediateVoidFuture()
        }
    }

    private fun urlOf(entry: Entry?): String? = entry?.url

    override fun handlePrepare(): ListenableFuture<*> = when {
        error != null -> loadLater(seekToMs = positionMs)
        pendingLoads.get() > 0 -> Futures.immediateVoidFuture()
        rendererUrl != null && rendererUrl == urlOf(currentEntry()) -> Futures.immediateVoidFuture()
        else -> loadLater(seekToMs = positionMs)
    }

    // A throw escaping here silently abandons every state write after it and nobody is told.
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        worker.submit<Unit> {
            try {
                if (playWhenReady) {
                    controlPoint.play(device)
                    this.playWhenReady = true
                    updateWakeLock()
                    playbackState = Player.STATE_READY
                    rendererStarted = false
                    pollsBeforeStart = 0
                    pendingSeekMs?.let {
                        pendingSeekMs = null
                        seekOnceStarted(it)
                    }
                    // Below the seek, above the lookahead. Either lands in the offset otherwise.
                    positionIsCurrentNow()
                    sendNextTrack()
                    handler.post { startPolling() }
                } else {
                    pauseOrStop()
                    positionMs = positionNow()
                    this.playWhenReady = false
                    updateWakeLock()
                }
            } catch (e: Exception) {
                if (playWhenReady) {
                    fail(classify(currentEntry(), e))
                } else {
                    Log.w(TAG, "renderer did not acknowledge the pause", e)
                    this.playWhenReady = false
                    updateWakeLock()
                    playbackState = Player.STATE_IDLE
                }
            }
            invalidateLater()
        }

    private fun pauseOrStop() {
        val allowed = controlPoint.currentTransportActions(device)
        if (allowed.isEmpty() || allowed.any { it.equals("Pause", ignoreCase = true) }) {
            try {
                controlPoint.pause(device)
                return
            } catch (e: UpnpException) {
                if (!e.isTransitionRefused) throw e
                Log.d(TAG, "renderer refused a pause, stopping instead")
            }
        }
        controlPoint.stop(device)
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        // Nothing above checks this. getState clamps what it reports, so controls act on the wrong track.
        if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex !in playlist.indices) {
            Log.w(TAG, "seek to index $mediaItemIndex is outside the queue, ignoring it")
            return Futures.immediateVoidFuture()
        }

        val target = if (positionMs == C.TIME_UNSET) 0L else positionMs
        val movingTrack = mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != index
        if (mediaItemIndex != C.INDEX_UNSET) index = mediaItemIndex

        // Nothing open means handing it over again, as after an ended queue.
        if (movingTrack || rendererUrl == null) {
            this.positionMs = target
            durationUs = C.TIME_UNSET
            return loadLater(seekToMs = target)
        }

        val generation =
            if (loadedGeneration == loadGeneration) ++loadGeneration else loadGeneration
        return worker.submit<Unit> {
            // Before the renderer has reported playing a seek waits for it, as one sent after a Play does.
            val taken =
                if (playWhenReady && !rendererStarted) seekOnceStarted(target) else seekWhenPlaying(target)
            // Only a seek the renderer took moves the bar, since a refused one keeps playing where it was.
            if (taken && generation == loadGeneration) {
                this@UpnpPlayer.positionMs = target
                playedHighWaterMs = target
            }
            loadedGeneration = generation
            invalidateLater()
        }
    }

    override fun handleStop(): ListenableFuture<*> = worker.submit<Unit> {
        stopRenderer("on stop")
        playWhenReady = false
        updateWakeLock()
        playbackState = Player.STATE_IDLE
        stopPolling()
        invalidateLater()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        stopPolling()
        updateWakeLock()
        return worker.submit<Unit> {
            stopRenderer("on release")
            worker.shutdown()
        }
    }

    private fun loadLater(seekToMs: Long): ListenableFuture<*> {
        val generation = ++loadGeneration
        pendingLoads.incrementAndGet()
        return worker.submit<Unit> {
            try {
                if (generation == loadGeneration) {
                    loadCurrent(seekToMs)
                    loadedGeneration = generation
                }
            } finally {
                pendingLoads.decrementAndGet()
            }
        }
    }

    private fun stopRenderer(why: String) {
        try {
            controlPoint.stop(device)
        } catch (e: Exception) {
            Log.d(TAG, "renderer did not acknowledge the stop $why", e)
        }
        rendererUrl = null
    }

    private fun loadCurrent(seekToMs: Long) {
        val entry = currentEntry() ?: return
        val url = entry.url ?: run {
            Log.w(TAG, "queue item has no uri, nothing to hand the renderer")
            return
        }

        error = null
        pendingSeekMs = null
        stoppedAtEndPolls = 0
        nextTrackSentFor = null
        rendererStarted = false
        pollsBeforeStart = 0
        playedHighWaterMs = 0
        playbackState = Player.STATE_BUFFERING
        invalidateLater()

        val restart = url == rendererUrl

        try {
            if (restart) {
                seekWhenPlaying(seekToMs)
            } else {
                controlPoint.setAvTransportUri(device, url, metadataFor(entry.item, url))
                rendererUrl = url
            }
            if (playWhenReady) controlPoint.play(device)
        } catch (e: Exception) {
            fail(classify(entry, e))
            return
        }

        if (!restart && seekToMs > 0) seekWhenPlaying(seekToMs, afterPlay = true)

        // Before the lookahead, or its round trip lands in the offset.
        positionIsCurrentNow()
        sendNextTrack()

        playbackState = Player.STATE_READY
        handler.post { if (playWhenReady) startPolling() }
        invalidateLater()
    }

    private fun classify(entry: Entry?, cause: Exception): PlaybackException =
        if (cause is UpnpException) {
            if (cause.isResourceMissing && entry != null) cannotUseTheStream(entry, cause)
            else refused(cause)
        } else {
            cannotReachRenderer(cause)
        }

    private fun cannotUseTheStream(entry: Entry, cause: Exception?): PlaybackException {
        val host = entry.url?.toUri()?.host ?: "the music server"
        return PlaybackException(
            "${device.friendlyName} could not play the track from $host. A renderer fetches the " +
                "stream from the music server itself, so the server address has to be one the " +
                "renderer can reach, not a VPN or loopback address only this phone can use.",
            cause,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
    }

    private fun refused(cause: UpnpException) = PlaybackException(
        "${device.friendlyName} refused the track (UPnP error ${cause.code}).",
        cause,
        PlaybackException.ERROR_CODE_REMOTE_ERROR
    )

    // Whether the target may be held as the position. A kept seek may, a refused one may not.
    private fun seekWhenPlaying(positionMs: Long, afterPlay: Boolean = false): Boolean =
        if (playWhenReady) {
            pendingSeekMs = null
            if (afterPlay) seekOnceStarted(positionMs) else seekOrKeepPlaying(positionMs)
        } else {
            pendingSeekMs = positionMs
            true
        }

    // A refused seek costs the position only and the track keeps playing. An LG C1 answers 711 or 501.
    private fun seekOrKeepPlaying(positionMs: Long): Boolean {
        seekSentAt = SystemClock.elapsedRealtime()
        return try {
            controlPoint.seek(device, asClock(positionMs))
            true
        } catch (e: Exception) {
            Log.d(TAG, "renderer did not take the seek target, it keeps playing from where it is", e)
            false
        }
    }

    // A WiiM Pro ignores a Seek sent right after Play, so this waits for playing, up to a cap.
    private fun seekOnceStarted(positionMs: Long): Boolean {
        val generation = loadGeneration
        val deadline = SystemClock.elapsedRealtime() + SEEK_WAIT_MS
        while (generation == loadGeneration && !released && SystemClock.elapsedRealtime() < deadline) {
            val state = try {
                controlPoint.transportInfo(device)["CurrentTransportState"]
            } catch (e: Exception) {
                break
            }
            if (state == "PLAYING") break
            SystemClock.sleep(SEEK_WAIT_POLL_MS)
        }
        return generation == loadGeneration && !released && seekOrKeepPlaying(positionMs)
    }

    // Remote error, not network failure, or the service reloads every five seconds forever.
    private fun cannotReachRenderer(cause: Exception) = PlaybackException(
        "Lost contact with ${device.friendlyName}.",
        cause,
        PlaybackException.ERROR_CODE_REMOTE_ERROR
    )

    // The renderer is stopped too, because an LG C1 left holding a URL it refused keeps refusing.
    private fun fail(reason: PlaybackException) {
        error = reason
        playWhenReady = false
        updateWakeLock()
        stopPolling()
        stopRenderer("after a refusal")
        invalidateLater()
        Log.w(TAG, "playback on the renderer stopped", reason)
    }

    // Only while playing, or filling the slot just consumed extends playback somebody asked to end.
    private fun sendNextTrack() {
        if (!playWhenReady) return
        val next = playlist.getOrNull(index + 1) ?: return
        val nextUrl = urlOf(next) ?: return
        try {
            controlPoint.setNextAvTransportUri(device, nextUrl, metadataFor(next.item, nextUrl))
            nextTrackSentFor = nextUrl
        } catch (e: Exception) {
            nextTrackSentFor = null
            Log.d(TAG, "renderer did not take a next track, queue will gap", e)
        }
    }

    // The service applies no threshold of its own, so the player decides what counted.
    private fun countsAsPlayed(playedMs: Long, lengthMs: Long): Boolean =
        lengthMs > 0 && (playedMs >= lengthMs - POLL_INTERVAL_MS ||
            MediaManager.meetsScrobbleThreshold(playedMs, lengthMs))

    private fun serverLengthMs(): Long =
        (currentEntry()?.item?.mediaMetadata?.extras?.getInt("duration", 0) ?: 0).toLong() * 1000L

    // TrackURI is the URL the renderer actually has open, and it drifts when it advances on its own.
    private fun followRenderer(trackUri: String?, playedMs: Long, lengthMs: Long): Boolean {
        if (trackUri.isNullOrBlank() || trackUri == urlOf(currentEntry())) return false

        // The handover first, since a song queued twice matches its earlier copy and goes backwards.
        val handedOver = index + 1
        val actual = if (urlOf(playlist.getOrNull(handedOver)) == trackUri) handedOver
        else playlist.indexOfFirst { urlOf(it) == trackUri }
        if (actual >= 0) {
            rendererAdvancedItself = countsAsPlayed(playedMs, lengthMs)
            index = actual
            durationUs = C.TIME_UNSET
            rendererStarted = false
            pollsBeforeStart = 0
            stoppedAtEndPolls = 0
            playedHighWaterMs = 0
            positionMs = 0
            invalidateLater()
            sendNextTrack()
            return true
        }

        if (!playWhenReady) return false
        Log.w(TAG, "renderer is on a track that is not in the queue, loading ours again")
        loadCurrent(seekToMs = 0L)
        return true
    }

    private fun readRemoteState() {
        if (released || !polling) return
        try {
            val sentAt = SystemClock.elapsedRealtime()
            val position = controlPoint.positionInfo(device)
            val arrivedAt = SystemClock.elapsedRealtime()
            val transport = controlPoint.transportInfo(device)
            failingSince = 0L

            if (loadedGeneration != loadGeneration) return

            // Read before this poll's reading, since the reply that catches a handover describes the next track.
            val barMs = positionNow()
            val playedMs = maxOf(barMs, playedHighWaterMs).also { playedHighWaterMs = it }
            val lengthMs = serverLengthMs().takeIf { it > 0 } ?: if (durationUs > 0) durationUs / 1000 else 0L
            val endMs = if (durationUs > 0) minOf(lengthMs, durationUs / 1000) else lengthMs

            // A zero never overwrites a known length, since chunked streams and handovers answer zero.
            asMillis(position["TrackDuration"])?.takeIf { it > 0 }?.let { durationUs = it * 1000 }
            asMillis(position["RelTime"])?.let { takeReading(it, sentAt, arrivedAt) }
            // A stopped LG C1 still reports its last URL, and following it replayed a finished track.
            if (transport["CurrentTransportState"] !in STOPPED_STATES) {
                position["TrackURI"]?.takeIf { it.isNotBlank() }?.let { rendererUrl = it }
                if (followRenderer(position["TrackURI"], playedMs, lengthMs)) return
            }

            // TRANSITIONING is also what a renderer answers while failing to fetch, so not started.
            when (transport["CurrentTransportState"]) {
                "PLAYING", "PAUSED_PLAYBACK" -> {
                    // Time spent buffering is not playback, and lands in the position otherwise.
                    if (playbackState != Player.STATE_READY) positionIsCurrentNow()
                    stoppedAtEndPolls = 0
                    rendererStarted = true
                    playbackState = Player.STATE_READY
                    followOutsidePlayPause(transport["CurrentTransportState"] == "PLAYING")
                }
                "TRANSITIONING" -> {
                    // getState freezes the bar on positionMs next, so make it current first.
                    if (playbackState == Player.STATE_READY && playWhenReady) positionMs = positionNow()
                    playbackState = Player.STATE_BUFFERING
                }
                in STOPPED_STATES -> onRemoteStopped(playedMs, lengthMs, endMs, barMs)
            }
        } catch (e: UpnpException) {
            failingSince = 0L
            if (e.isResourceMissing && playWhenReady) {
                currentEntry()?.let { fail(cannotUseTheStream(it, e)) } ?: run {
                    Log.w(TAG, "could not read the renderer's state", e)
                }
            } else {
                Log.w(TAG, "could not read the renderer's state", e)
            }
        } catch (e: Exception) {
            if (failingSince == 0L) failingSince = SystemClock.uptimeMillis()
            when {
                SystemClock.uptimeMillis() - failingSince <= GIVE_UP_AFTER_MS ->
                    Log.w(TAG, "could not read the renderer's state", e)
                playWhenReady -> fail(cannotReachRenderer(e))
                else -> {
                    Log.w(TAG, "renderer is unreachable while paused, no longer reading it", e)
                    rendererUrl = null
                    playbackState = Player.STATE_IDLE
                    stopPolling()
                }
            }
        }
        invalidateLater()
    }

    // Only a stop while playing, on a renderer seen playing, can end a track.
    private fun onRemoteStopped(playedMs: Long, lengthMs: Long, endMs: Long, barMs: Long) {
        if (!playWhenReady) {
            rendererUrl = null
            playbackState = Player.STATE_IDLE
            return
        }

        val entry = currentEntry()
        if (entry != null && !rendererStarted) {
            // Stopped before it starts, so the URL is still open.
            pollsBeforeStart += 1
            if (pollsBeforeStart > MAX_POLLS_BEFORE_START) fail(cannotUseTheStream(entry, null))
            return
        }
        rendererUrl = null

        // Far from the end the stop came from outside, so the queue stays on the track.
        if (endMs > 0 && playedMs < endMs - END_WINDOW_MS) {
            // A stopped renderer reads zero, so the bar from before this poll is kept.
            positionMs = barMs
            playWhenReady = false
            updateWakeLock()
            playbackState = Player.STATE_IDLE
            stopPolling()
            return
        }

        // A renderer holding the next track reports stopped for a moment before starting it.
        val next = playlist.getOrNull(index + 1)
        val holdsNext = next != null && nextTrackSentFor == urlOf(next)
        if (holdsNext && stoppedAtEndPolls < MAX_POLLS_AWAITING_HANDOVER) {
            stoppedAtEndPolls += 1
            return
        }
        stoppedAtEndPolls = 0

        val counted = countsAsPlayed(playedMs, lengthMs)
        if (index + 1 < playlist.size) {
            rendererAdvancedItself = counted
            index += 1
            positionMs = 0
            durationUs = C.TIME_UNSET
            rendererStarted = false
            pollsBeforeStart = 0
            playedHighWaterMs = 0
            loadCurrent(seekToMs = 0)
        } else {
            playWhenReady = false
            updateWakeLock()
            // The service submits an ended queue on its own, so a stop that did not count is idle.
            playbackState = if (counted) Player.STATE_ENDED else Player.STATE_IDLE
            stopPolling()
        }
    }

    private fun followOutsidePlayPause(rendererPlaying: Boolean) {
        if (rendererPlaying == playWhenReady) {
            outsidePlayPausePolls = 0
            return
        }
        if (++outsidePlayPausePolls < OUTSIDE_PLAY_PAUSE_POLLS) return
        outsidePlayPausePolls = 0

        if (!rendererPlaying) positionMs = positionNow()
        playWhenReady = rendererPlaying
        updateWakeLock()
        if (!rendererPlaying) return

        // Held back while paused, and no Play from the app is coming to send them.
        pendingSeekMs?.let {
            pendingSeekMs = null
            if (seekOrKeepPlaying(it)) {
                positionMs = it
                playedHighWaterMs = it
            }
        }
        sendNextTrack()
    }

    private fun currentEntry(): Entry? = playlist.getOrNull(index)

    private fun metadataFor(item: MediaItem, url: String): String {
        val metadata = item.mediaMetadata
        // Tempus tags every item "audio", so protocolInfo keeps a wildcard media type. C1 plays FLAC.
        return UpnpControlPoint.didl(
            url,
            metadata.title?.toString().orEmpty(),
            metadata.artist?.toString().orEmpty(),
            metadata.albumTitle?.toString().orEmpty(),
            "*"
        )
    }

    private fun startPolling() {
        if (released) return
        polling = true
        failingSince = 0L
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    private fun stopPolling() {
        polling = false
        handler.post { handler.removeCallbacks(poll) }
    }

    // Held while driving playback, not while polling, or a paused cast holds it.
    @SuppressLint("Wakelock", "WakelockTimeout")
    private fun updateWakeLock() {
        val lock = wakeLock ?: return
        val wanted = polling && playWhenReady && !released
        if (wanted && !lock.isHeld) lock.acquire() else if (!wanted && lock.isHeld) lock.release()
    }

    private fun positionIsCurrentNow() {
        positionAt = SystemClock.elapsedRealtime()
    }

    private fun barCeiling(): Long =
        if (durationUs <= 0) Long.MAX_VALUE else durationUs / 1000 - 1

    // Held below the length, or media3 reads the next correction as a track ending.
    private fun positionNow(): Long =
        (positionMs + if (positionAt == 0L) 0L else SystemClock.elapsedRealtime() - positionAt)
            .coerceAtMost(barCeiling())

    private fun takeReading(reading: Long, sentAt: Long, arrivedAt: Long) {
        // Above the playing gate, since the poll after a transition carries the handover's nonsense.
        val lengthMs = if (durationUs == C.TIME_UNSET) Long.MAX_VALUE else durationUs / 1000
        if (reading >= lengthMs) return
        // A reading asked for around a seek is refused, since a refetching renderer answers from before it.
        if (sentAt - seekSentAt < POLL_INTERVAL_MS &&
            abs(reading - positionNow()) >= REBASE_MS
        ) {
            return
        }
        if (playbackState != Player.STATE_READY || !playWhenReady) {
            positionMs = reading
            return
        }
        if (arrivedAt - sentAt > STALE_READING_MS) return
        val here = positionNow()
        if (abs(reading - here) < REBASE_MS) {
            positionMs = here
            return
        }
        positionMs = reading
        positionAt = (sentAt + arrivedAt) / 2
    }

    private fun invalidateLater() = handler.post { if (!released) invalidateState() }

    companion object {
        private const val TAG = "UpnpPlayer"

        private val POLL_INTERVAL_MS = TimeUnit.SECONDS.toMillis(2)

        private val REBASE_MS = TimeUnit.SECONDS.toMillis(2)

        private val STALE_READING_MS = POLL_INTERVAL_MS * 2

        private val STOPPED_STATES = setOf("STOPPED", "NO_MEDIA_PRESENT")

        private const val WAKE_LOCK_TAG = "tempus:UpnpPlayer"

        private const val MAX_POLLS_BEFORE_START = 5

        // Stopped polls waited out at a track end when the renderer holds the next one.
        private const val MAX_POLLS_AWAITING_HANDOVER = 3

        private const val OUTSIDE_PLAY_PAUSE_POLLS = 2

        // A stop this close to a track's end is the track ending, and anything earlier came from outside.
        private val END_WINDOW_MS = TimeUnit.SECONDS.toMillis(10)

        private val GIVE_UP_AFTER_MS = TimeUnit.SECONDS.toMillis(30)

        private val SEEK_WAIT_MS = TimeUnit.SECONDS.toMillis(5)

        private const val SEEK_WAIT_POLL_MS = 200L

        // The format the app asked the server for, else the format of the file on the server.
        private fun mimeTypeFor(item: MediaItem, url: String?): String {
            val extras = item.mediaMetadata.extras
            val asked = when (extras?.getString("type")) {
                Constants.MEDIA_TYPE_MUSIC, Constants.MEDIA_TYPE_PODCAST ->
                    url?.toUri()?.getQueryParameter("format")
                else -> null
            }
            val format = asked?.takeIf { it.isNotEmpty() && it != "raw" }
                ?: MusicUtil.sourceSuffix(extras)?.takeIf { it.isNotEmpty() }
                ?: return MimeTypes.AUDIO_UNKNOWN
            // Format.Builder normalizes it, so mp3 lands as audio/mpeg.
            return "audio/$format"
        }

        /** On every format this player publishes, so the player screen knows a renderer plays. */
        const val FORMAT_ID = "upnp"

        // Without tracks onTracksChanged never fires, and scrobbling and continuous play hang off it.
        private fun audioTracks(uid: Long, mimeType: String): Tracks {
            val format = Format.Builder().setId(FORMAT_ID).setSampleMimeType(mimeType).build()
            val group = TrackGroup(uid.toString(), format)
            return Tracks(
                listOf(
                    Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))
                )
            )
        }

        // No volume, since an LG C1 reports zero while audibly playing.
        private val COMMANDS = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SET_MEDIA_ITEM,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            // Without it the session strips the tracks from what a controller sees.
            Player.COMMAND_GET_TRACKS,
            Player.COMMAND_RELEASE
        ).build()

        // Never the item's own URL, which a download recorded long ago and a renderer has to fetch.
        @JvmStatic
        fun streamUrlFor(item: MediaItem): String? =
            if (item.mediaMetadata.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC) {
                MusicUtil.getStreamUri(item.mediaId).toString()
            } else {
                item.localConfiguration?.uri?.toString()
            }

        @JvmStatic
        fun asMillis(clock: String?): Long? {
            val parts = clock?.trim()?.split(':') ?: return null
            if (parts.size != 3) return null
            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = parts[1].toLongOrNull() ?: return null
            val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return null
            if (minutes >= 60 || seconds >= 60) return null
            return ((hours * 60 + minutes) * 60 + seconds) * 1000
        }

        @JvmStatic
        fun asClock(millis: Long): String {
            val total = (millis / 1000).coerceAtLeast(0)
            return "%02d:%02d:%02d".format(total / 3600, (total / 60) % 60, total % 60)
        }
    }
}
