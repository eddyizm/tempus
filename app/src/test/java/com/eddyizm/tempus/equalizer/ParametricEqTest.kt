package com.eddyizm.tempus.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class ParametricEqTest {

    private val sample = """
        Preamp: -6.1 dB
        Filter 1: ON LSC Fc 105 Hz Gain 5.5 dB Q 0.70
        Filter 2: ON PK Fc 180 Hz Gain -3.0 dB Q 0.80
        Filter 3: OFF PK Fc 1000 Hz Gain 2.0 dB Q 1.00
        Filter 4: ON HSC Fc 10000 Hz Gain -2.0 dB Q 0.70
    """.trimIndent()

    @Test
    fun parsesSampleSkippingOffFilters() {
        val profile = ParametricProfile.parse(sample)!!
        assertEquals(-6.1, profile.preampDb, 1e-9)
        assertEquals(3, profile.filters.size)
        assertEquals(FilterType.LOW_SHELF, profile.filters[0].type)
        assertEquals(FilterType.PEAK, profile.filters[1].type)
        assertEquals(FilterType.HIGH_SHELF, profile.filters[2].type)
    }

    @Test
    fun normalizedTextParsesBackToTheSameProfile() {
        val profile = ParametricProfile.parse(sample)!!
        val again = ParametricProfile.parse(profile.toText())!!
        assertEquals(profile.preampDb, again.preampDb, 0.0)
        assertEquals(profile.filters, again.filters)
    }

    @Test
    fun filtersMatchTheirAnalogPrototype() {
        for (fs in listOf(44100, 48000)) for (filter in listOf(
            ParametricFilter(FilterType.PEAK, 180.0, -3.0, 0.8),
            ParametricFilter(FilterType.LOW_SHELF, 100.0, 6.0, 2.0),
            ParametricFilter(FilterType.HIGH_SHELF, 3000.0, 6.0, 2.0),
            ParametricFilter(FilterType.PEAK, 12500.0, 4.0, 2.0)
        )) {
            val bq = Biquad.of(filter, fs)
            assertTrue("$filter is unstable", abs(bq.a2) < 1 && abs(bq.a1) < 1 + bq.a2)
            for (freq in listOf(filter.fc / 2, filter.fc, filter.fc * 2).filter { it < fs / 2.0 }) {
                val expected = analogResponse(filter, freq, fs)
                val actual = digitalResponse(bq, freq, fs)
                assertEquals("$filter at $freq Hz of $fs, real part", expected[0], actual[0], 1e-9)
                assertEquals("$filter at $freq Hz of $fs, imaginary part", expected[1], actual[1], 1e-9)
            }
        }
    }

    @Test
    fun preampScalesTheSignal() {
        val flat = ParametricFilter(FilterType.PEAK, 1000.0, 0.0, 1.0)
        val profile = ParametricProfile(-6.0206, listOf(flat, flat.copy(fc = 2000.0)))
        assertEquals(0.5, ParametricEqDsp(profile, 48000, 1).process(1.0, 0), 1e-5)
        assertEquals(2.0, ParametricEqDsp(ParametricProfile(6.0206, profile.filters), 48000, 1).process(1.0, 0), 1e-5)
    }

    @Test
    fun fullScaleSineSaturatesWithoutWrapping() {
        val fs = 48000
        val freq = 1000.0
        val profile = ParametricProfile.parse("Preamp: 0 dB\nFilter 1: ON PK Fc 1000 Hz Gain 10 dB Q 1.0")!!
        val input = ShortArray(fs) { (Short.MAX_VALUE * sin(2 * PI * freq * it / fs)).toInt().toShort() }
        val output = run(processor(profile, fs, channels = 1), input)
        var clipped = 0
        for (n in input.indices) {
            val x = input[n].toInt()
            val y = output[n].toInt()
            if (n > fs / 10 && abs(x) > Short.MAX_VALUE / 2) {
                assertEquals("sample $n", x.sign, y.sign)
                assertTrue("sample $n = $y", abs(y) >= Short.MAX_VALUE)
                clipped++
            }
        }
        assertTrue(clipped > 0)
    }

    @Test
    fun filtersNearNyquistAreSkipped() {
        for ((fs, fc) in listOf(44100 to 20000.0, 44100 to 19845.0, 48000 to 21600.0)) {
            val dsp = ParametricEqDsp(ParametricProfile(0.0, listOf(ParametricFilter(FilterType.PEAK, fc, 10.0, 1.0))), fs, 1)
            assertEquals("$fc Hz of $fs", 1.0, dsp.process(1.0, 0), 0.0)
            for (n in 1 until 64) assertEquals("$fc Hz of $fs", 0.0, dsp.process(0.0, 0), 0.0)
        }
    }

    @Test
    fun filterJustBelowTheSkipLimitIsKeptAtThePlaybackRate() {
        for ((fs, fc) in listOf(44100 to 19800.0, 48000 to 21500.0)) {
            val filter = ParametricFilter(FilterType.PEAK, fc, 6.0, 1.0)
            val bq = Biquad.of(filter, fs)
            val dsp = ParametricEqDsp(ParametricProfile(0.0, listOf(filter)), fs, 1)
            val h0 = bq.b0
            val h1 = bq.b1 - bq.a1 * h0
            val h2 = bq.b2 - bq.a1 * h1 - bq.a2 * h0
            assertEquals("$fc Hz of $fs", h0, dsp.process(1.0, 0), 1e-12)
            assertEquals("$fc Hz of $fs", h1, dsp.process(0.0, 0), 1e-12)
            assertEquals("$fc Hz of $fs", h2, dsp.process(0.0, 0), 1e-12)
        }
    }

    @Test
    fun eachFilterFeedsTheNext() {
        val first = ParametricFilter(FilterType.PEAK, 1000.0, 6.0, 1.0)
        val second = ParametricFilter(FilterType.PEAK, 3000.0, -4.0, 2.0)
        val cascade = ParametricEqDsp(ParametricProfile(0.0, listOf(first, second)), 48000, 1)
        val a = ParametricEqDsp(ParametricProfile(0.0, listOf(first)), 48000, 1)
        val b = ParametricEqDsp(ParametricProfile(0.0, listOf(second)), 48000, 1)
        for (n in 0 until 64) {
            val x = if (n == 0) 1.0 else 0.0
            assertEquals("sample $n", b.process(a.process(x, 0), 0), cascade.process(x, 0), 1e-12)
        }
    }

    @Test
    fun perEarExportIsRejected() {
        val text = """
            Channel: L
            Preamp: -5 dB
            Filter 1: ON PK Fc 100 Hz Gain 3 dB Q 1

            Channel: R
            Preamp: -4 dB
            Filter 1: ON PK Fc 105 Hz Gain 2.5 dB Q 1
            """.trimIndent()
        assertTrue(ParametricProfile.isPerEarExport(text))
        assertNull(ParametricProfile.parse(text))
    }

    @Test
    fun graphicEqFileIsRejected() {
        assertNull(ParametricProfile.parse("GraphicEQ: 20 -3.2; 21 -3.1; 22 -3.0; 23 -2.9"))
    }

    @Test
    fun unusableFilterLineIsSkipped() {
        val kept = ParametricFilter(FilterType.PEAK, 200.0, 3.0, 50.0)
        for (bad in listOf(
            "ON XYZ Fc 100 Hz Gain 3 dB Q 1.0",
            "ON PK Fc 100 Hz Gain 3 dB Q 0.001",
            "ON PK Fc 1${"0".repeat(400)} Hz Gain 3 dB Q 1.0",
            "ON PK Fc 1000 Hz Gain 3 dB Q 1,41",
            "ON LSC Fc 100 Hz Gain 3 dB Q 50",
            "ON LSC Fc 0.000001 Hz Gain 3 dB Q 0.7"
        )) {
            val profile = ParametricProfile.parse("Preamp: -1 dB\nFilter 1: $bad\nFilter 2: ON PK Fc 200 Hz Gain 3 dB Q 50")!!
            assertEquals(bad, listOf(kept), profile.filters)
        }
    }

    @Test
    fun leadingBomIsIgnored() {
        val profile = ParametricProfile.parse("\uFEFF" + sample)!!
        assertEquals(-6.1, profile.preampDb, 1e-9)
    }

    @Test
    fun unreadablePreampRejectsTheFile() {
        assertNull(ParametricProfile.parse("Preamp: -6,4 dB\nFilter 1: ON PK Fc 1000 Hz Gain 2 dB Q 1.0"))
    }

    @Test
    fun tinyValuesRoundTripWithoutScientificNotation() {
        val profile = ParametricProfile(-0.0005, listOf(ParametricFilter(FilterType.PEAK, 1000.0, 0.0005, 1.0)))
        val again = ParametricProfile.parse(profile.toText())!!
        assertEquals(profile.preampDb, again.preampDb, 0.0)
        assertEquals(profile.filters, again.filters)
    }

    @Test
    fun shelfAliasesParseAsShelves() {
        val profile = ParametricProfile.parse(
            "Preamp: 0 dB\nFilter 1: ON LSQ Fc 70 Hz Gain 1.0 dB Q 1.000\nFilter 2: ON HSQ Fc 15000 Hz Gain 7.0 dB Q 2.000\nFilter 3: ON LS Fc 100 Hz Gain 3 dB Q 0.7"
        )!!
        assertEquals(listOf(FilterType.LOW_SHELF, FilterType.HIGH_SHELF, FilterType.LOW_SHELF), profile.filters.map { it.type })
    }

    @Test
    fun twentyFiltersParseAndTwentyOneReject() {
        val lines = (1..21).map { "Filter $it: ON PK Fc ${100 * it} Hz Gain 1 dB Q 1.0" }
        assertEquals(20, ParametricProfile.parse("Preamp: 0 dB\n" + lines.take(20).joinToString("\n"))!!.filters.size)
        assertNull(ParametricProfile.parse("Preamp: 0 dB\n" + lines.joinToString("\n")))
    }

    @Test
    fun sameFormatFlushKeepsFilterState() {
        for (fs in listOf(44100, 48000)) {
            val input = ShortArray(2 * 4096) { (10000 * sin(2 * PI * 50.0 * (it / 2) / fs)).toInt().toShort() }
            val half = input.size / 2
            val profile = ParametricProfile.parse(sample)!!

            val continuous = processor(profile, fs)
            val expected = run(continuous, input)

            val flushed = processor(profile, fs)
            val first = run(flushed, input.copyOfRange(0, half))
            flushed.configure(AudioFormat(fs, 2, C.ENCODING_PCM_16BIT))
            flushed.flush(StreamMetadata.DEFAULT)
            val actual = first + run(flushed, input.copyOfRange(half, input.size))

            assertEquals(input.size, actual.size)
            assertTrue("$fs", expected.contentEquals(actual))
        }
    }

    @Test
    fun processorMatchesOneMonoDspPerChannel() {
        for (fs in listOf(44100, 48000)) {
            val freqs = listOf(50.0, 3000.0, 200.0, 800.0, 6000.0, 12000.0)
            val input = ShortArray(6 * 2048) {
                (10000 * sin(2 * PI * freqs[it % 6] * (it / 6) / fs)).toInt().toShort()
            }
            val profile = ParametricProfile.parse(sample)!!
            val mono = Array(6) { ParametricEqDsp(profile, fs, 1) }
            val expected = ShortArray(input.size) { mono[it % 6].process16(input[it], 0) }
            assertTrue("$fs", expected.contentEquals(run(processor(profile, fs, channels = 6), input)))
        }
    }

    @Test
    fun profileSetDuringPlaybackTakesEffect() {
        val fs = 44100
        val input = ShortArray(2 * 2048) { (10000 * sin(2 * PI * 50.0 * (it / 2) / fs)).toInt().toShort() }
        val profile = ParametricProfile.parse(sample)!!
        val expected = run(processor(profile, fs), input)

        val late = processor(null, fs)
        late.setProfile(profile)
        assertTrue(expected.contentEquals(run(late, input)))
    }

    @Test
    fun profileChangedOrClearedDuringPlaybackTakesEffect() {
        val fs = 44100
        val input = ShortArray(2 * 2048) { (10000 * sin(2 * PI * 50.0 * (it / 2) / fs)).toInt().toShort() }
        val other = ParametricProfile.parse("Preamp: 0 dB\nFilter 1: ON PK Fc 1000 Hz Gain 6 dB Q 1.0")!!
        val expected = run(processor(other, fs), input)

        val changed = processor(ParametricProfile.parse(sample)!!, fs)
        run(changed, input)
        changed.setProfile(other)
        assertTrue(expected.contentEquals(run(changed, input)))

        changed.setProfile(null)
        assertTrue(input.contentEquals(run(changed, input)))
    }

    @Test
    fun sampleRateChangeRebuildsTheFilters() {
        for ((from, to) in listOf(44100 to 48000, 48000 to 44100)) {
            val input = ShortArray(2 * 2048) { (10000 * sin(2 * PI * 50.0 * (it / 2) / to)).toInt().toShort() }
            val profile = ParametricProfile.parse(sample)!!
            val expected = run(processor(profile, to), input)

            val changed = processor(profile, from)
            run(changed, input)
            changed.configure(AudioFormat(to, 2, C.ENCODING_PCM_16BIT))
            changed.flush(StreamMetadata.DEFAULT)
            assertTrue("$from to $to", expected.contentEquals(run(changed, input)))
        }
    }

    @Test
    fun channelCountChangeRebuildsTheFilters() {
        for ((from, to) in listOf(1 to 2, 2 to 1, 2 to 6)) {
            val input = ShortArray(6 * 2048) { (10000 * sin(2 * PI * 50.0 * (it / 2) / 44100)).toInt().toShort() }
            val profile = ParametricProfile.parse(sample)!!
            val expected = run(processor(profile, 44100, channels = to), input)

            val changed = processor(profile, 44100, channels = from)
            run(changed, input)
            changed.configure(AudioFormat(44100, to, C.ENCODING_PCM_16BIT))
            changed.flush(StreamMetadata.DEFAULT)
            assertTrue("$from to $to", expected.contentEquals(run(changed, input)))
        }
    }

    @Test
    fun encodingChangeRebuildsTheFilters() {
        val changed = processor(ParametricProfile.parse(sample)!!, 44100)
        run(changed, ShortArray(2 * 2048) { (10000 * sin(2 * PI * 50.0 * (it / 2) / 44100)).toInt().toShort() })
        val floatFormat = AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        assertEquals(floatFormat, changed.configure(floatFormat))
        changed.flush(StreamMetadata.DEFAULT)
        changed.queueInput(ByteBuffer.allocateDirect(2 * 64 * 4).order(ByteOrder.nativeOrder()))
        val output = changed.output.order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertEquals(2 * 64, output.remaining())
        while (output.hasRemaining()) assertEquals(0f, output.get(), 0f)
    }

    @Test
    fun unsupportedEncodingIsNotAccepted() {
        for (enc in listOf(C.ENCODING_PCM_8BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val format = ParametricEqAudioProcessor().configure(AudioFormat(44100, 2, enc))
            assertEquals("$enc", AudioFormat.NOT_SET, format)
        }
    }

    private fun processor(profile: ParametricProfile?, fs: Int, channels: Int = 2) = ParametricEqAudioProcessor().apply {
        setProfile(profile)
        val format = AudioFormat(fs, channels, C.ENCODING_PCM_16BIT)
        assertEquals(format, configure(format))
        flush(StreamMetadata.DEFAULT)
    }

    private fun run(processor: ParametricEqAudioProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        input.asShortBuffer().put(samples)
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder()).asShortBuffer()
        return ShortArray(output.remaining()).also { output.get(it) }
    }

    private fun analogResponse(f: ParametricFilter, freq: Double, fs: Int): DoubleArray {
        val a = 10.0.pow(f.gainDb / 40)
        val w = tan(PI * freq / fs) / tan(PI * f.fc / fs)
        val k = sqrt(a) / f.q * w
        return when (f.type) {
            FilterType.PEAK -> divide(1 - w * w, a / f.q * w, 1 - w * w, w / (a * f.q))
            FilterType.LOW_SHELF -> divide(a * (a - w * w), a * k, 1 - a * w * w, k)
            FilterType.HIGH_SHELF -> divide(a * (1 - a * w * w), a * k, a - w * w, k)
        }
    }

    private fun digitalResponse(bq: Biquad, freq: Double, fs: Int): DoubleArray {
        val w = 2 * PI * freq / fs
        return divide(
            bq.b0 + bq.b1 * cos(w) + bq.b2 * cos(2 * w), -(bq.b1 * sin(w) + bq.b2 * sin(2 * w)),
            1 + bq.a1 * cos(w) + bq.a2 * cos(2 * w), -(bq.a1 * sin(w) + bq.a2 * sin(2 * w))
        )
    }

    private fun divide(nr: Double, ni: Double, dr: Double, di: Double): DoubleArray {
        val d = dr * dr + di * di
        return doubleArrayOf((nr * dr + ni * di) / d, (ni * dr - nr * di) / d)
    }
}
