package com.eddyizm.tempus.util

import okhttp3.Protocol
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomHeadersHttpClientTest {
    @Test
    fun speaksHttp11Only_soAReconnectGetsANewConnection() {
        // Over HTTP/2 a new request shares the connection the stall watchdog gave up on.
        assertEquals(listOf(Protocol.HTTP_1_1), DownloadUtil.buildCustomHeadersHttpClient().protocols)
    }
}
