package com.eddyizm.tempus.lan

/** Validation shared by the sender and the receiver; independent of Android state. */
object LanPolicy {
    fun validateQueue(ids: List<String>, index: Int, position: Long) {
        require(ids.size in 1..500)
        require(index in ids.indices)
        require(position >= 0)
        require(ids.all { it.length in 1..512 })
        // The legacy whole-queue transfer accepts unique songs. Editable remote
        // queues use LanQueuePolicy and preserve duplicate occurrences.
        require(ids.distinct().size == ids.size)
    }

    fun mayPair(command: String, now: Long, pairingUntil: Long): Boolean =
        command == "hello" && now < pairingUntil

    fun mayApprove(now: Long, pairingUntil: Long, requestedAt: Long): Boolean =
        now < pairingUntil && now >= requestedAt && now - requestedAt < 120000
}
