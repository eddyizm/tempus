package com.eddyizm.tempus.equalizer

import java.math.BigDecimal
import kotlin.math.abs

enum class FilterType(val code: String) { PEAK("PK"), LOW_SHELF("LSC"), HIGH_SHELF("HSC") }

data class ParametricFilter(val type: FilterType, val fc: Double, val gainDb: Double, val q: Double)

class ParametricProfile(val preampDb: Double, val filters: List<ParametricFilter>) {

    fun toText(): String = buildString {
        append("Preamp: ").append(plain(preampDb)).append(" dB\n")
        filters.forEachIndexed { i, f ->
            append("Filter ${i + 1}: ON ${f.type.code} Fc ${plain(f.fc)} Hz Gain ${plain(f.gainDb)} dB Q ${plain(f.q)}\n")
        }
    }

    private fun plain(x: Double): String = BigDecimal.valueOf(x).toPlainString()

    companion object {
        const val MAX_FILTERS = 20
        const val MAX_GAIN_DB = 20.0
        const val MIN_FC_HZ = 10.0
        const val MAX_SHELF_Q = 10.0

        private val PREAMP = Regex("""^Preamp:\s*([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
        private val FILTER = Regex(
            """^Filter\s*\d*:\s*(ON|OFF)\s+(\w+)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz\s+Gain\s+([-+]?\d+(?:\.\d+)?)\s*dB\s+Q\s+(\d+(?:\.\d+)?)\s*$""",
            RegexOption.IGNORE_CASE
        )

        @JvmStatic
        fun isPerEarExport(text: String): Boolean =
            text.lineSequence().any { it.trim().startsWith("Channel:", ignoreCase = true) }

        @JvmStatic
        fun parse(text: String): ParametricProfile? {
            if (isPerEarExport(text)) return null
            var preamp = 0.0
            val filters = ArrayList<ParametricFilter>()
            for (raw in text.removePrefix("\uFEFF").lineSequence()) {
                val line = raw.trim()
                val p = PREAMP.find(line)
                if (p != null) {
                    preamp = p.groupValues[1].toDouble()
                    if (abs(preamp) > MAX_GAIN_DB) return null
                } else if (line.startsWith("preamp", ignoreCase = true)) {
                    return null
                }
                val m = FILTER.find(line) ?: continue
                if (m.groupValues[1].equals("OFF", ignoreCase = true)) continue
                val type = when (m.groupValues[2].uppercase()) {
                    "PK" -> FilterType.PEAK
                    "LSC", "LS", "LSQ" -> FilterType.LOW_SHELF
                    "HSC", "HS", "HSQ" -> FilterType.HIGH_SHELF
                    else -> continue
                }
                val fc = m.groupValues[3].toDouble()
                val gain = m.groupValues[4].toDouble()
                val q = m.groupValues[5].toDouble()
                if (!fc.isFinite() || !q.isFinite() || fc < MIN_FC_HZ || q < 0.01 || abs(gain) > MAX_GAIN_DB) continue
                if (type != FilterType.PEAK && q > MAX_SHELF_Q) continue
                filters.add(ParametricFilter(type, fc, gain, q))
            }
            if (filters.isEmpty() || filters.size > MAX_FILTERS) return null
            return ParametricProfile(preamp, filters)
        }
    }
}
