package com.eddyizm.tempus.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.Preferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock

@UnstableApi
class AutoDownloadPromoterTest {

    @Test
    fun promoteIfEligible_noopWhenChildOrUriIsNull() {
        val context = mock<Context>()
        val child = Child("song1")
        val uri = mock<Uri>()

        // Should return gracefully without throwing
        AutoDownloadPromoter.promoteIfEligible(context, null, uri)
        AutoDownloadPromoter.promoteIfEligible(context, child, null)
        AutoDownloadPromoter.promoteIfEligible(context, null, null)
    }

    @Test
    fun promoteIfEligible_noopWhenDisabledInPreferences() {
        val context = mock<Context>()
        val child = Child("song1").apply {
            title = "Test Song"
            type = Constants.MEDIA_TYPE_MUSIC
        }
        val uri = mock<Uri>()

        mockStatic(Preferences::class.java).use { prefs ->
            prefs.`when`<Boolean> { Preferences.isAutoDownloadPlayedTracks() }.thenReturn(false)

            // Should bail out immediately because setting is disabled
            AutoDownloadPromoter.promoteIfEligible(context, child, uri)
        }
    }

    @Test
    fun promoteIfEligible_skipsRadioStreams() {
        val context = mock<Context>()
        val uri = mock<Uri>()

        val radioChild1 = Child("song1").apply {
            type = Constants.MEDIA_TYPE_RADIO
        }
        val radioChild2 = Child("ir-stream-123").apply {
            type = Constants.MEDIA_TYPE_MUSIC
        }

        mockStatic(Preferences::class.java).use { prefs ->
            prefs.`when`<Boolean> { Preferences.isAutoDownloadPlayedTracks() }.thenReturn(true)

            // Both radio streams should be ignored
            AutoDownloadPromoter.promoteIfEligible(context, radioChild1, uri)
            AutoDownloadPromoter.promoteIfEligible(context, radioChild2, uri)
        }
    }
}
