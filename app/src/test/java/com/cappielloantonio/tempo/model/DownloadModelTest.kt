package com.cappielloantonio.tempo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadModelTest {

    @Test
    fun defaults_reflectDownloadingState() {
        val d = Download("id-1")
        assertEquals("id-1", d.id)
        // State 0 = actively downloading (transfers in progress).
        assertEquals(0, d.downloadState)
        // NOTE: the Kotlin object default is null; the Room @ColumnInfo
        // defaultValue = "" only applies to the stored column, not the field.
        assertNull(d.downloadUri)
    }

    @Test
    fun downloadState_isMutable_andTracksCompletion() {
        val d = Download("id-2")
        // After completion the repository sets download_state = 1.
        d.downloadState = 1
        assertEquals(1, d.downloadState)

        // Any non-0/1 value must survive (used to mark removed/failed rows).
        d.downloadState = 2
        assertEquals(2, d.downloadState)
    }

    @Test
    fun downloadUri_isMutable() {
        val d = Download("id-3")
        d.downloadUri = "/storage/emulated/0/Music/Artist - Title.mp3"
        assertEquals("/storage/emulated/0/Music/Artist - Title.mp3", d.downloadUri)
    }
}
