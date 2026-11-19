package com.eddyizm.tempus.util

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The fix for #383 is an invariant about which source a period is built from. Looping exposes it,
 * because the player builds the next loop before it releases the finishing one.
 */
class TranscodingMediaSourceTest {

    private val allocator = mock<Allocator>()
    private val periodId = MediaSource.MediaPeriodId(Any())

    private class Child(val source: ProgressiveMediaSource, val period: MediaPeriod)

    private class Factory {
        val children = mutableListOf<Child>()

        val mock: ProgressiveMediaSource.Factory = mock<ProgressiveMediaSource.Factory>().also {
            whenever(it.createMediaSource(any())).thenAnswer {
                val source = mock<ProgressiveMediaSource>()
                val period = mock<MediaPeriod>()
                whenever(source.createPeriod(any(), any(), any())).thenReturn(period)
                children.add(Child(source, period))
                source
            }
        }

        val base get() = children[0]
        val seekChild get() = children[1]
    }

    private fun prepared(
            factory: Factory,
            item: MediaItem = MediaItem.Builder().setMediaId("song-1").build()
    ): TranscodingMediaSource {
        val source = TranscodingMediaSource(item, mock<DataSource.Factory>(), factory.mock)
        source.prepareSource(mock<MediaSource.MediaSourceCaller>(), null, PlayerId.UNSET)
        return source
    }

    /** The composite base class reaches for these while the source is prepared. */
    private fun withAndroidLooper(body: () -> Unit) {
        mockStatic(Looper::class.java).use { looper ->
            looper.`when`<Looper> { Looper.myLooper() }.thenReturn(mock<Looper>())
            mockConstruction(Handler::class.java).use { body() }
        }
    }

    // A transcode the player cannot seek reports an adjusted position of zero, which forces reload.
    private fun seek(period: MediaPeriod, factory: Factory) {
        whenever(factory.base.period.getAdjustedSeekPositionUs(any(), any<SeekParameters>()))
                .thenReturn(0L)
        mockStatic(MusicUtil::class.java).use { music ->
            music.`when`<Uri> { MusicUtil.getStreamUri(any(), any()) }.thenReturn(mock<Uri>())
            period.seekToUs(6_000_000L)
        }
    }

    @Test
    fun everyLoopIsBuiltFromTheOneBaseSource() = withAndroidLooper {
        val factory = Factory()
        val source = prepared(factory)

        source.createPeriod(periodId, allocator, 0)
        source.createPeriod(periodId, allocator, 0)

        assertEquals(1, factory.children.size)
        verify(factory.base.source, times(2)).createPeriod(any(), any(), any())
    }

    @Test
    fun aLoopBuiltAfterASeekStillComesFromTheBaseSource() = withAndroidLooper {
        val factory = Factory()
        val source = prepared(factory)

        seek(source.createPeriod(periodId, allocator, 0), factory)
        source.createPeriod(periodId, allocator, 0)

        assertEquals(2, factory.children.size)
        // The base served the first playthrough and the loop; before the fix the loop came off
        // the seek source instead.
        verify(factory.base.source, times(2)).createPeriod(any(), any(), any())
        verify(factory.seekChild.source, times(1)).createPeriod(any(), any(), any())
    }

    private fun song(uri: Uri, title: String) =
            MediaItem.Builder()
                    .setMediaId("song-1")
                    .setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                    .build()

    @Test
    fun aChangeToTheDetailsOnlyIsUpdatedInPlace() {
        val uri = mock<Uri>()
        val source = TranscodingMediaSource(song(uri, "a"), mock<DataSource.Factory>(), Factory().mock)

        assertTrue(source.canUpdateMediaItem(song(uri, "b")))
    }

    @Test
    fun aNewStreamAddressIsNotUpdatedInPlace() {
        val source = TranscodingMediaSource(song(mock<Uri>(), "a"), mock<DataSource.Factory>(), Factory().mock)

        assertFalse(source.canUpdateMediaItem(song(mock<Uri>(), "a")))
    }

    @Test
    fun anUpdateIsKeptAndPassedToTheBaseSource() = withAndroidLooper {
        val uri = mock<Uri>()
        val factory = Factory()
        val source = prepared(factory, song(uri, "a"))
        val starred = song(uri, "b")

        source.updateMediaItem(starred)

        assertSame(starred, source.mediaItem)
        verify(factory.base.source).updateMediaItem(starred)
    }
}
