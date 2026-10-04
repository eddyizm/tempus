package com.eddyizm.tempus.lan

/** Queue edits are checked before touching the receiver player. Duplicate tracks are valid. */
object LanQueuePolicy {
    const val LIMIT = 500
    fun matchesRevision(displayed: String, actual: String): Boolean = displayed.isNotEmpty() && displayed == actual
    fun validateSongs(ids: List<String>) {
        require(ids.size in 1..LIMIT)
        require(ids.all { it.length in 1..512 && !it.startsWith("ir_") })
    }
    fun validateInsertion(currentCount: Int, addedCount: Int) {
        require(currentCount >= 0 && addedCount > 0 && currentCount.toLong() + addedCount <= LIMIT)
    }
    fun validateRange(count: Int, from: Int, to: Int) {
        require(from in 0..count && to in from..count)
    }
    fun validateMove(count: Int, from: Int, to: Int) {
        require(from in 0 until count && to in 0 until count)
    }
    fun validateReorder(current: List<String>, ordered: List<String>) {
        require(current.groupingBy { it }.eachCount() == ordered.groupingBy { it }.eachCount())
    }
}
