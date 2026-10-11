package com.eddyizm.tempus.model

/**
 * The server a request is going to, as passed to a
 * [HeaderSource][com.eddyizm.tempus.util.HeaderSource].
 */
data class ServerContext(
    /** Addresses of the server (public, local, in use). Headers are only sent to these origins. */
    val addresses: List<String?>,
    /** The user's raw custom headers text, see [CustomHeaders][com.eddyizm.tempus.util.CustomHeaders]. */
    val customHeaders: String?,
)
