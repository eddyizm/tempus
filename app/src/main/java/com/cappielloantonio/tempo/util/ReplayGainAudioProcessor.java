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

    private volatile float baselineGainLinear = 1.0f;
    
    private volatile boolean hasPendingFlushGain = false;

    private float activeGainLinear = 1.0f;

    private float rampFromGain = 1.0f;

    private float rampToGain = 1.0f;

    private int rampTotalFrames = 441;

    private int rampFramesDone = 0;

    private boolean ramping = false;

    // Set to true when onConfigure() is called while endOfStreamPending is true.
    // A gapless transition with a format change always triggers onConfigure()
    // between onQueueEndOfStream() and onFlush(), whereas a seek within the
    // same track (even after the decoder has run ahead) never changes the format
    // and therefore never calls onConfigure(). This lets onFlush() distinguish
    // the two cases and avoid applying the next-track's gain after a seek.
    private boolean reconfiguredSinceEndOfStream = false;

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
        float linear = dbToLinear(gainDb);
        targetGainLinear = linear;
        baselineGainLinear = linear;
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
        // If we're in an end-of-stream state, a reconfigure means this is a
        // gapless transition with a format change (not a seek). Record this so
        // onFlush() can distinguish the two cases.
        if (endOfStreamPending) {
            reconfiguredSinceEndOfStream = true;
        }
        return inputAudioFormat;
    }

    @Override
    protected void onFlush() {
        // Only promote the pending gain if this flush represents a gapless
        // transition with an audio format change. The three required conditions:
        //
        //  1. hasPendingFlushGain  — a gain was queued for the next track.
        //  2. hasProcessedAnyInput — real audio has already flowed through,
        //                            so this is not the initial startup flush.
        //  3. endOfStreamPending   — the decoder reached the end of the
        //                            current stream before the flush.
        //  4. reconfiguredSinceEndOfStream — onConfigure() fired between
        //                            onQueueEndOfStream() and here, which only
        //                            happens on a format-change gapless
        //                            transition, NOT on a seek within the
        //                            same track (even if the decoder ran ahead).
        //
        // Without condition 4, a seek that happens after the decoder has
        // already reached end-of-stream (decoder runs ahead of audio output)
        // is indistinguishable from a gapless format-change. Without the guard,
        // the seek incorrectly promotes the next track's (or fallback) gain,
        // causing an audible volume jump on resume.
        if (hasPendingFlushGain && hasProcessedAnyInput
                && endOfStreamPending && reconfiguredSinceEndOfStream) {
            activeGainLinear = pendingFlushGainLinear;
            targetGainLinear = pendingFlushGainLinear;
            hasPendingFlushGain = false;
            ramping = false;
        }
        endOfStreamPending = false;
        reconfiguredSinceEndOfStream = false;
        hasProcessedAnyInput = false;
    }

    /**
     * Called when the current stream has no more input.  For gapless
     * transitions with the same audio format, media3 1.8's DefaultAudioSink
     * does NOT call flush() between tracks — only queueEndOfStream() —
     * so the pending gain for that case is promoted in queueInput() below
     * when the first samples of the new track arrive.
     *
     * For gapless transitions with a format change, onConfigure() fires
     * after this and sets reconfiguredSinceEndOfStream = true, allowing
     * onFlush() to safely promote the pending gain there instead.
     *
     * We intentionally do NOT change activeGainLinear/targetGainLinear here.
     * The old behaviour of doing so was the source of the seek volume bug:
     * if the decoder ran ahead to the end of the current track while the
     * user was still listening mid-track, and the user then sought backward,
     * the next-track gain was already baked into activeGainLinear and could
     * not be undone by onFlush() (which could no longer distinguish the seek
     * from a legitimate gapless transition).
     */
    @Override
    protected void onQueueEndOfStream() {
        endOfStreamPending = true;
    }

    @Override
    protected void onReset() {
        activeGainLinear = baselineGainLinear;
        targetGainLinear = baselineGainLinear;
        pendingFlushGainLinear = 1.0f;
        hasPendingFlushGain = false;
        endOfStreamPending = false;
        reconfiguredSinceEndOfStream = false;
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

        // Gapless same-format transition: onQueueEndOfStream() was called
        // (decoder finished the previous track) and the first samples of the
        // new track are now arriving without any intervening onFlush() or
        // onConfigure(). This is the only opportunity to apply the pending
        // gain for same-format gapless playback.
        if (endOfStreamPending && hasPendingFlushGain) {
            targetGainLinear = pendingFlushGainLinear;
            hasPendingFlushGain = false;
            endOfStreamPending = false;
        }

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
