package com.eddyizm.tempus.util

import android.content.SharedPreferences
import com.eddyizm.tempus.App
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContinueListeningSwitchTest {

    /** Runs [block] against preferences holding only [stored]; any other key reads its default. */
    private fun withPreferences(stored: Map<String, Boolean>, block: () -> Unit) {
        val prefs = mock<SharedPreferences>()
        whenever(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer {
            stored[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1)
        }
        val app = mock<App>()
        whenever(app.preferences).thenReturn(prefs)

        mockStatic(App::class.java).use { appStatic ->
            appStatic.`when`<App> { App.getInstance() }.thenReturn(app)
            block()
        }
    }

    @Test
    fun onUntilTurnedOff() {
        // 4.29.0 shipped it on; an update must not quietly stop saving places.
        withPreferences(emptyMap()) {
            assertTrue(Preferences.isContinueListeningEnabled())
        }
    }

    @Test
    fun readsWhatWasSaved() {
        withPreferences(mapOf(Preferences.CONTINUE_LISTENING_ENABLED to false)) {
            assertFalse(Preferences.isContinueListeningEnabled())
        }
    }
}
