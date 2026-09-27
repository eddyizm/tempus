package com.eddyizm.tempus.lan

/** Fixed diagnostic categories; never display exception messages or server credentials. */
internal class LanConnectionFailure(val stage: Stage, cause: Exception) : Exception(cause) {
    enum class Stage { IDENTITY, CONNECT, TLS, VERIFY, RESPONSE }
}
