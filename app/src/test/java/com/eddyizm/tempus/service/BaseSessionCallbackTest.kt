package com.eddyizm.tempus.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Rating
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSession.ControllerInfo
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.FavoriteRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic

class BaseSessionCallbackTest {

    @After
    fun dropTheRegistryListener() {
        FavoriteRegistry.onChange = null
        FavoriteRegistry.clear()
    }

    @Test
    fun updateMediaNotificationCustomLayout_doesNotCrashWhenControllerInfoIsNull() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val session = mock<MediaSession>()
        val player = mock<Player>()
        val mediaMetadata = mock<MediaMetadata>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")
        whenever(session.player).thenReturn(player)
        whenever(player.mediaMetadata).thenReturn(mediaMetadata)
        whenever(session.mediaNotificationControllerInfo).thenReturn(null)

        mockConstruction(SessionCommand::class.java).use {
            val callback = object : BaseSessionCallback(context, service) {
                fun triggerUpdate() {
                    updateMediaNotificationCustomLayout(session)
                }
            }
            callback.triggerUpdate()
        }
    }

    @Test
    fun onConnect_registersListenerOnlyOnce() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val session = mock<MediaSession>()
        val controller = mock<ControllerInfo>()
        val player = mock<Player>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")
        whenever(session.player).thenReturn(player)
        // Assume NOT a notification controller for simplicity in this test
        whenever(session.isMediaNotificationController(any())).thenReturn(false)
        whenever(session.isAutomotiveController(any())).thenReturn(false)
        whenever(session.isAutoCompanionController(any())).thenReturn(false)

        mockConstruction(SessionCommand::class.java).use {
            val callback = BaseSessionCallback(context, service)
            
            callback.onConnect(session, controller)
            callback.onConnect(session, controller)
            
            // Should be called only once because of currentSession check
            verify(player, times(1)).addListener(any())
        }
    }

    @Test
    fun handlePlayerChanged_beforeOnConnect_doesNotRegisterListener() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val session = mock<MediaSession>()
        val controller = mock<ControllerInfo>()
        val player = mock<Player>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")
        whenever(session.player).thenReturn(player)
        whenever(session.isMediaNotificationController(any())).thenReturn(false)
        whenever(session.isAutomotiveController(any())).thenReturn(false)
        whenever(session.isAutoCompanionController(any())).thenReturn(false)

        mockConstruction(SessionCommand::class.java).use {
            val callback = BaseSessionCallback(context, service)
            
            // 1. Player changes before any controller connects (currentSession is null)
            callback.handlePlayerChanged(null, player)
            
            // 2. Controller connects
            callback.onConnect(session, controller)
            
            // Should be registered ONLY ONCE (by onConnect)
            verify(player, times(1)).addListener(any())
        }
    }

    @Test
    fun handlePlayerChanged_afterOnConnect_movesListener() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val session = mock<MediaSession>()
        val controller = mock<ControllerInfo>()
        val oldPlayer = mock<Player>()
        val newPlayer = mock<Player>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")
        whenever(session.player).thenReturn(oldPlayer)
        whenever(session.isMediaNotificationController(any())).thenReturn(false)
        whenever(session.isAutomotiveController(any())).thenReturn(false)
        whenever(session.isAutoCompanionController(any())).thenReturn(false)

        mockConstruction(SessionCommand::class.java).use {
            val callback = BaseSessionCallback(context, service)
            
            // 1. Initial connection
            callback.onConnect(session, controller)
            verify(oldPlayer, times(1)).addListener(any())
            
            // 2. Player changes (e.g. switch to Cast)
            callback.handlePlayerChanged(oldPlayer, newPlayer)
            
            // Should move the listener
            verify(oldPlayer).removeListener(any())
            verify(newPlayer).addListener(any())
        }
    }

    @Test
    fun onConnect_armsTheRegistryListener() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val session = mock<MediaSession>()
        val controller = mock<ControllerInfo>()
        val player = mock<Player>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")
        whenever(session.player).thenReturn(player)
        whenever(session.isMediaNotificationController(any())).thenReturn(false)
        whenever(session.isAutomotiveController(any())).thenReturn(false)
        whenever(session.isAutoCompanionController(any())).thenReturn(false)

        FavoriteRegistry.onChange = null

        mockConstruction(SessionCommand::class.java).use {
            val callback = BaseSessionCallback(context, service)
            callback.onConnect(session, controller)

            assertNotNull(FavoriteRegistry.onChange)
        }
    }

    @Test
    fun heartButton_readsTheRegistryWhenTheItemRatingIsStale() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val player = mock<Player>()
        val app = mock<App>()
        val preferences = mock<SharedPreferences>()
        val extras = mock<Bundle>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")
        whenever(extras.getString("type")).thenReturn(Constants.MEDIA_TYPE_MUSIC)
        whenever(preferences.getString(any(), any())).thenReturn("[heartID]")
        whenever(app.preferences).thenReturn(preferences)

        // The item was loaded before the star, so its own rating is the stale half of the bug.
        val metadata = MediaMetadata.Builder()
            .setExtras(extras)
            .setUserRating(HeartRating(false))
            .build()
        val item = MediaItem.Builder().setMediaId("song1").setMediaMetadata(metadata).build()
        whenever(player.currentMediaItem).thenReturn(item)
        whenever(player.mediaMetadata).thenReturn(metadata)

        FavoriteRegistry.set(FavoriteRegistry.Kind.SONG, "song1", true)

        mockStatic(App::class.java).use { appStatic ->
            appStatic.`when`<App> { App.getInstance() }.thenReturn(app)

            mockConstruction(SessionCommand::class.java).use {
                val callback = object : BaseSessionCallback(context, service) {
                    fun firstButton() = buildCustomLayout(player).first()
                }

                assertEquals(R.drawable.ic_favorite, callback.firstButton().iconResId)
            }
        }
    }
    @Test
    fun heartTap_sendsUnstarWhenTheRegistrySaysStarredAndTheItemRatingIsStale() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val session = mock<MediaSession>()
        val controller = mock<ControllerInfo>()
        val player = mock<Player>()
        val extras = mock<Bundle>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")
        whenever(extras.getString("type")).thenReturn(Constants.MEDIA_TYPE_MUSIC)
        whenever(session.player).thenReturn(player)

        // The item was loaded before the star, so its own rating is the stale half of the bug.
        val metadata = MediaMetadata.Builder()
            .setExtras(extras)
            .setUserRating(HeartRating(false))
            .build()
        val item = MediaItem.Builder().setMediaId("song1").setMediaMetadata(metadata).build()
        whenever(player.currentMediaItem).thenReturn(item)
        whenever(player.mediaMetadata).thenReturn(metadata)

        FavoriteRegistry.set(FavoriteRegistry.Kind.SONG, "song1", true)

        val sent = mutableListOf<HeartRating>()
        val command = SessionCommand(Constants.CUSTOM_COMMAND_TOGGLE_HEART_ON, mock<Bundle>())

        mockConstruction(SessionCommand::class.java).use {
            val callback = object : BaseSessionCallback(context, service) {
                override fun onSetRating(
                    session: MediaSession,
                    controller: ControllerInfo,
                    rating: Rating
                ): ListenableFuture<SessionResult> {
                    sent.add(rating as HeartRating)
                    return Futures.immediateFuture(mock<SessionResult>())
                }
            }

            callback.onCustomCommand(session, controller, command, mock<Bundle>())
        }

        assertEquals(1, sent.size)
        assertFalse(sent[0].isHeart)
    }
}
