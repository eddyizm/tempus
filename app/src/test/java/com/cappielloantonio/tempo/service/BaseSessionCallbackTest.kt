package com.cappielloantonio.tempo.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.MediaSession.ControllerInfo
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mockConstruction

class BaseSessionCallbackTest {

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
    fun handlePlayerChanged_movesListener() {
        val context = mock<Context>()
        val service = mock<BaseMediaService>()
        val oldPlayer = mock<Player>()
        val newPlayer = mock<Player>()

        whenever(context.getString(anyInt())).thenReturn("mock_string")

        mockConstruction(SessionCommand::class.java).use {
            val callback = BaseSessionCallback(context, service)
            
            callback.handlePlayerChanged(oldPlayer, newPlayer)
            
            verify(oldPlayer).removeListener(any())
            verify(newPlayer).addListener(any())
        }
    }
}
