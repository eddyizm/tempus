package com.eddyizm.tempus.lan

import android.util.Log
import java.util.ArrayDeque

/** Memory-only, bounded diagnostics. Callers must use fixed events, never peer data. */
internal object LanDiagnostics {
    private const val LIMIT = 200
    private val entries = ArrayDeque<String>()

    @Synchronized fun record(event: String, failure: Throwable? = null) {
        // Exception messages and stack traces can contain URLs or credentials.
        val types = generateSequence(failure) { it.cause }.take(6)
            .joinToString(" -> ") { it.javaClass.simpleName }
        val line = "${System.currentTimeMillis()} $event" + if (types.isEmpty()) "" else " [$types]"
        if (entries.size == LIMIT) entries.removeFirst()
        entries.addLast(line)
        Log.d("TempusLAN", line)
    }

    @Synchronized fun snapshot(): String = entries.joinToString("\n")
}
