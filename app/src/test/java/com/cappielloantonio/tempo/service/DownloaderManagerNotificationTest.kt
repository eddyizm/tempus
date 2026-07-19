package com.cappielloantonio.tempo.service

import com.cappielloantonio.tempo.model.Download
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class DownloaderManagerNotificationTest {

    @After
    fun tearDown() {
        DownloaderManager.setMetadataCache(ConcurrentHashMap())
    }

    @Test
    fun notificationMessage_returnsArtistDashFilename() {
        val download = Download("tr-1")
        download.title = "Cool Song"
        download.downloadUri = "content://external/some-id/Some Artist - Cool Song (Album).opus"

        val cache = ConcurrentHashMap<String, Download>()
        cache["tr-1"] = download
        DownloaderManager.setMetadataCache(cache)

        assertEquals(
            "Some Artist - Cool Song (Album).opus",
            DownloaderManager.getDownloadNotificationMessage("tr-1")
        )
    }

    @Test
    fun notificationMessage_stripsQueryStringFromStreamUri() {
        val download = Download("tr-2")
        download.artist = "The Band"
        download.title = "Track Nine"
        download.downloadUri = "http://host:5082/rest/stream?u=x&id=tr-2&format=opus"

        val cache = ConcurrentHashMap<String, Download>()
        cache["tr-2"] = download
        DownloaderManager.setMetadataCache(cache)

        assertEquals("stream", DownloaderManager.getDownloadNotificationMessage("tr-2"))
    }

    @Test
    fun notificationMessage_fallsBackToTitleWhenNoUri() {
        val download = Download("tr-3")
        download.title = "Untitled"
        download.suffix = "flac"
        download.downloadUri = null

        val cache = ConcurrentHashMap<String, Download>()
        cache["tr-3"] = download
        DownloaderManager.setMetadataCache(cache)

        assertEquals("Untitled.flac", DownloaderManager.getDownloadNotificationMessage("tr-3"))
    }

    @Test
    fun notificationMessage_unknownIdReturnsNull() {
        DownloaderManager.setMetadataCache(ConcurrentHashMap())
        assertEquals(null, DownloaderManager.getDownloadNotificationMessage("missing"))
    }
}
