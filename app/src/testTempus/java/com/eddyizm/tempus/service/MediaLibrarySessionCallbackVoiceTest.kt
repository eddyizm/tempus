package com.eddyizm.tempus.service

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import com.eddyizm.tempus.repository.AutomotiveRepository
import com.eddyizm.tempus.util.ConstantsAA
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutionException

class MediaLibrarySessionCallbackVoiceTest {

    private val context = mock<Context>().also {
        whenever(it.getString(anyInt())).thenReturn("mock_string")
        whenever(it.applicationContext).thenReturn(it)
    }
    private val repository = mock<AutomotiveRepository>()

    @After
    fun dropTheHandler() {
        MediaServiceExtensionRegistry.handler = null
    }

    private fun voiceRequest(query: String) = MediaItem.Builder()
        .setMediaId("")
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query).build())
        .build()

    private fun searchReturns(vararg items: MediaItem) {
        whenever(repository.search(anyString(), anyString(), anyString()))
            .thenReturn(Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), null)))
    }

    private fun setVoiceRequest(query: String) = mockConstruction(SessionCommand::class.java).use {
        MediaLibrarySessionCallback(context, mock(), repository)
            .onSetMediaItems(mock<MediaSession>(), mock(), listOf(voiceRequest(query)), C.INDEX_UNSET, C.TIME_UNSET)
    }

    @Test
    fun queryWithNoMatchFailsSoCurrentPlaybackContinues() {
        searchReturns()

        val result = setVoiceRequest("zqxvjk")

        assertThrows(ExecutionException::class.java) { result.get() }
    }

    @Test
    fun artistWithoutAMixFailsAndLeavesNoMixExtender() {
        val artist = MediaItem.Builder()
            .setMediaId(ConstantsAA.ARTIST_ID + "ar1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Adele").setIsBrowsable(true).setIsPlayable(false).build())
            .build()
        searchReturns(artist)
        whenever(repository.getInstantMix("ar1"))
            .thenReturn(Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)))

        val result = setVoiceRequest("adele")

        assertThrows(ExecutionException::class.java) { result.get() }
        assertNull(MediaServiceExtensionRegistry.handler)
    }
}
