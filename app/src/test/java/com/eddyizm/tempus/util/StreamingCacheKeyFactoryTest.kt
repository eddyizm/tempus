package com.eddyizm.tempus.util

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@UnstableApi
class StreamingCacheKeyFactoryTest {

    @Test
    fun networkTrackUsesNormalizedKeyInsteadOfInheritedUrlKey() {
        val uri = mock<Uri>()
        whenever(uri.scheme).thenReturn("https")
        whenever(uri.getQueryParameter("id")).thenReturn("track-1")
        whenever(uri.getQueryParameter("maxBitRate")).thenReturn("128")
        whenever(uri.getQueryParameter("format")).thenReturn("mp3")
        whenever(uri.getQueryParameter("timeOffset")).thenReturn(null)
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .setKey("inherited-url-cache-key")
            .build()

        mockStatic(Preferences::class.java).use { preferences ->
            preferences.`when`<String?> { Preferences.getServerId() }.thenReturn("server-1")

            val key = StreamingCacheKeyFactory().buildCacheKey(dataSpec)

            assertEquals("server-1|track-1|128|mp3|", key)
        }
    }
}
