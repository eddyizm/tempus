package com.eddyizm.tempus.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
    companion object {
        fun of(f: ParametricFilter, sampleRate: Int): Biquad {
            val a = 10.0.pow(f.gainDb / 40.0)
            val w0 = 2.0 * PI * f.fc / sampleRate
            val cw = cos(w0)
            val alpha = sin(w0) / (2.0 * f.q)
            val s = 2.0 * sqrt(a) * alpha
            val c = when (f.type) {
                FilterType.PEAK -> doubleArrayOf(
                    1 + alpha * a, -2 * cw, 1 - alpha * a,
                    1 + alpha / a, -2 * cw, 1 - alpha / a
                )
                FilterType.LOW_SHELF -> doubleArrayOf(
                    a * ((a + 1) - (a - 1) * cw + s), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - s),
                    (a + 1) + (a - 1) * cw + s, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - s
                )
                FilterType.HIGH_SHELF -> doubleArrayOf(
                    a * ((a + 1) + (a - 1) * cw + s), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - s),
                    (a + 1) - (a - 1) * cw + s, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - s
                )
            }
            val a0 = c[3]
            return Biquad(c[0] / a0, c[1] / a0, c[2] / a0, c[4] / a0, c[5] / a0)
        }
    }
}

class ParametricEqDsp(profile: ParametricProfile, sampleRate: Int, private val channels: Int) {
    private val preamp = 10.0.pow(profile.preampDb / 20.0)
    private val stages = profile.filters
        .filter { it.fc < 0.45 * sampleRate }
        .map { Biquad.of(it, sampleRate) }
        .toTypedArray()
    private val state = DoubleArray(stages.size * channels * 2)

    fun process(x: Double, channel: Int): Double {
        var y = x * preamp
        var i = channel * stages.size * 2
        for (bq in stages) {
            val input = y
            y = bq.b0 * input + state[i]
            state[i] = bq.b1 * input - bq.a1 * y + state[i + 1]
            state[i + 1] = bq.b2 * input - bq.a2 * y
            i += 2
        }
        return y
    }

    fun process16(x: Short, channel: Int): Short = saturate16(process(x.toDouble(), channel))

    companion object {
        fun saturate16(x: Double): Short =
            x.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

/**
 * Applies an imported AutoEq profile to PCM inside ExoPlayer's pipeline. It stays active for
 * every 16 bit and float format and bypasses inside [queueInput] while no profile is set, so a
 * profile set mid queue takes effect without waiting for a format change.
 */
@UnstableApi
class ParametricEqAudioProcessor : BaseAudioProcessor() {
    @Volatile private var profile: ParametricProfile? = null
    private var builtFor: ParametricProfile? = null
    private var dsp: ParametricEqDsp? = null
    private var builtFormat = AudioFormat.NOT_SET

    fun setProfile(profile: ParametricProfile?) {
        this.profile = profile
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioFormat.NOT_SET
        return inputAudioFormat
    }

    // media3 flushes at every gapless transition, so keep the filter state unless something changed.
    override fun onFlush() {
        if (dsp == null || profile !== builtFor || inputAudioFormat != builtFormat) rebuild()
    }

    override fun onReset() {
        builtFor = null
        dsp = null
    }

    private fun rebuild() {
        val current = profile
        builtFor = current
        builtFormat = inputAudioFormat
        dsp = if (current == null || inputAudioFormat == AudioFormat.NOT_SET) null
        else ParametricEqDsp(current, inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (profile !== builtFor) rebuild()

        val output = replaceOutputBuffer(remaining)
        val eq = dsp
        if (eq == null) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        output.order(ByteOrder.nativeOrder())
        val channels = inputAudioFormat.channelCount
        var ch = 0
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.remaining() >= 2) {
                output.putShort(eq.process16(inputBuffer.getShort(), ch))
                if (++ch == channels) ch = 0
            }
        } else {
            while (inputBuffer.remaining() >= 4) {
                output.putFloat(eq.process(inputBuffer.getFloat().toDouble(), ch).toFloat())
                if (++ch == channels) ch = 0
            }
        }
        output.flip()
    }
}
