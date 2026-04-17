package com.cappielloantonio.tempo.util;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An AudioProcessor that applies ReplayGain adjustment directly to
 * PCM samples.
 *
 * Unlike player.setVolume() or LoudnessEnhancer, this
 * processor operates inside ExoPlayer's audio pipeline, which means
 * gain changes are sample-accurate during gapless transitions.
 */
@OptIn(markerClass = UnstableApi.class)
public final class ReplayGainAudioProcessor extends BaseAudioProcessor {

    private static final float RAMP_DURATION_SECONDS = 0.01f; // 10 ms

    private volatile float targetGainLinear = 1.0f;

    private volatile float pendingFlushGainLinear = 1.0f;

    private volatile boolean hasPendingFlushGain = false;

    private float activeGainLinear = 1.0f;

    private float rampFromGain = 1.0f;

    private float rampToGain = 1.0f;

    private int rampTotalFrames = 441;

    private int rampFramesDone = 0;

    private boolean ramping = false;

    // Tracks whether any samples have been processed since the last flush.
    // onFlush() is called both at initial audio-sink configuration (before
    // any audio has played) AND at mid-stream format-change transitions.
    // We only want to consume the pending gain in the second case — at
    // startup, pending was queued for the NEXT track and must not be
    // applied to the first track. Flipped true in queueInput and reset
    // to false in onFlush / onReset.
    private boolean hasProcessedAnyInput = false;

    private boolean endOfStreamPending = false;

    public void setPendingGain(float gainDb) {
        pendingFlushGainLinear = dbToLinear(gainDb);
        hasPendingFlushGain = true;
    }

    public void setGainImmediate(float gainDb) {
        targetGainLinear = dbToLinear(gainDb);
        hasPendingFlushGain = false;
    }

    public void clearPendingGain() {
        hasPendingFlushGain = false;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws UnhandledAudioFormatException {
        int enc = inputAudioFormat.encoding;
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET;
        }
        rampTotalFrames = Math.max(1,
                (int) (inputAudioFormat.sampleRate * RAMP_DURATION_SECONDS));
        return inputAudioFormat;   
    }

    @Override
    protected void onFlush() {
        // Only promote the pending gain if this flush represents a real
        // mid-stream boundary (some samples have already flowed through
        // the processor). The initial configure→flush that runs before
        // the first track starts playing must NOT steal the pending gain
        // that was queued for the next track — doing so applies track
        // B's gain to track A.
        // endOfStreamPending guards against seeks: ExoPlayer calls onFlush()
        // for both seeks and format-change track transitions, but only calls
        // onQueueEndOfStream() for the latter. Without this check a seek
        // mid-track incorrectly promotes the next track's (or fallback) gain.
        if (hasPendingFlushGain && hasProcessedAnyInput && endOfStreamPending) {
            activeGainLinear = pendingFlushGainLinear;
            targetGainLinear = pendingFlushGainLinear;
            hasPendingFlushGain = false;
            ramping = false;
        }
        endOfStreamPending = false;
        hasProcessedAnyInput = false;
    }

    /**
     * Called when the current stream has no more input.  For gapless
     * transitions with the same audio format, media3 1.8's DefaultAudioSink
     * does NOT call flush() between tracks — only queueEndOfStream().
     * Activating the pending gain here (in addition to onFlush) ensures the
     * next track's first samples get the correct gain in queueInput, since
     * onQueueEndOfStream fires at the decoder stream boundary BEFORE the
     * next track's samples enter the pipeline.
     */
    @Override
    protected void onQueueEndOfStream() {
        endOfStreamPending = true;
        if (hasPendingFlushGain) {
            activeGainLinear = pendingFlushGainLinear;
            targetGainLinear = pendingFlushGainLinear;
            hasPendingFlushGain = false;
            ramping = false;
        }
    }

    @Override
    protected void onReset() {
        activeGainLinear = 1.0f;
        targetGainLinear = 1.0f;
        pendingFlushGainLinear = 1.0f;
        hasPendingFlushGain = false;
        endOfStreamPending = false;
        ramping = false;
        hasProcessedAnyInput = false;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) return;

        // Mark that real audio has flowed through the processor, so a
        // subsequent onFlush() knows it's a mid-stream boundary (safe to
        // consume pending) rather than the initial startup flush.
        hasProcessedAnyInput = true;

        float target = targetGainLinear;
        if (!ramping && Math.abs(target - activeGainLinear) > 0.0001f) {
            rampFromGain = activeGainLinear;
            rampToGain = target;
            rampFramesDone = 0;
            ramping = true;
        }

        if (!ramping && Math.abs(activeGainLinear - 1.0f) < 0.0001f) {
            ByteBuffer output = replaceOutputBuffer(remaining);
            output.put(inputBuffer);
            output.flip();
            return;
        }

        ByteBuffer output = replaceOutputBuffer(remaining);
        output.order(ByteOrder.nativeOrder());

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            process16Bit(inputBuffer, output);
        } else {
            processFloat(inputBuffer, output);
        }

        output.flip();
    }

    private void process16Bit(ByteBuffer in, ByteBuffer out) {
        while (in.remaining() >= 2) {
            float gain = advanceGain();
            short sample = in.getShort();
            float adjusted = sample * gain;
            if (adjusted > Short.MAX_VALUE) adjusted = Short.MAX_VALUE;
            else if (adjusted < Short.MIN_VALUE) adjusted = Short.MIN_VALUE;
            out.putShort((short) adjusted);
        }
    }

    private void processFloat(ByteBuffer in, ByteBuffer out) {
        while (in.remaining() >= 4) {
            float gain = advanceGain();
            out.putFloat(in.getFloat() * gain);
        }
    }

    private float advanceGain() {
        if (!ramping) return activeGainLinear;

        float t = (float) rampFramesDone / rampTotalFrames;
        float gain = rampFromGain + (rampToGain - rampFromGain) * t;
        rampFramesDone++;
        if (rampFramesDone >= rampTotalFrames) {
            activeGainLinear = rampToGain;
            ramping = false;
        }
        return gain;
    }

    private static float dbToLinear(float db) {
        db = Math.max(-60f, Math.min(15f, db));
        return (float) Math.pow(10.0, db / 20.0);
    }
}
