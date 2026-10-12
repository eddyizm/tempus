package com.eddyizm.tempus.service

import androidx.core.content.ContextCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.cast.CastMediaItemConverter
import com.eddyizm.tempus.repository.AutomotiveRepository
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

@UnstableApi
class MediaService : BaseMediaService(), SessionAvailabilityListener {
    private val automotiveRepository = AutomotiveRepository()
    private lateinit var castPlayer: CastPlayer
    private lateinit var automotiveLyricsUpdater: AutomotiveLyricsUpdater

    @Suppress("DEPRECATION")
    private fun initializeCastPlayer() {
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        ) {
            CastContext.getSharedInstance(this, ContextCompat.getMainExecutor(this))
                .addOnSuccessListener { castContext ->
                    castPlayer = CastPlayer(castContext, CastMediaItemConverter())
                    castPlayer.setSessionAvailabilityListener(this@MediaService)
                    initializePlayerListener(castPlayer)
                    if (castPlayer.isCastSessionAvailable)
                        setPlayer(mediaLibrarySession.player, castPlayer)
                }
        }
    }

    override fun getMediaLibrarySessionCallback(): MediaLibrarySession.Callback {
        if (sessionCallback == null) {
            sessionCallback = MediaLibrarySessionCallback(baseContext, this, automotiveRepository)
        }
        return sessionCallback!!
    }

    override fun playerInitHook() {
        super.playerInitHook()
        automotiveLyricsUpdater = AutomotiveLyricsUpdater(mediaLibrarySession, exoplayer)
        automotiveLyricsUpdater.start()
        initializeCastPlayer()
        if (this::castPlayer.isInitialized && castPlayer.isCastSessionAvailable)
            setPlayer(null, castPlayer)
    }

    override fun releasePlayers() {
        if (this::automotiveLyricsUpdater.isInitialized) automotiveLyricsUpdater.release()
        if (this::castPlayer.isInitialized) {
            castPlayer.setSessionAvailabilityListener(null)
            castPlayer.release()
        }
        automotiveRepository.deleteMetadata()
        super.releasePlayers()
    }

    override fun onCastSessionAvailable() {
        setPlayer(exoplayer, castPlayer)
    }

    override fun onCastSessionUnavailable() {
        setPlayer(castPlayer, exoplayer)
    }
}