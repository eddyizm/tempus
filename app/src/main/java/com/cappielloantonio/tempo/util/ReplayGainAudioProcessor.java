package com.cappielloantonio.tempo.util;

import android.util.Log;
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

    private static final String TAG = "RGAudioProcessor";
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

    // Tracks whether any samples have been processed since the last flush.
    // onFlush() is called both at initial audio-sink configuration (before
    // any audio has played) AND at mid-stream format-change transitions.
    // We only want to consume the pending gain in the second case - at
    // startup, pending was queued for the NEXT track and must not be
    // applied to the first track. Flipped true in queueInput and reset
    // to false in onFlush / onReset.
    private boolean hasProcessedAnyInput = false;

    private boolean endOfStreamPending = false;

    // Set to true in onConfigure() and cleared in onFlush() / onReset().
    // onConfigure() is only called by ExoPlayer when the audio format
    // actually changes — which happens for format-change gapless track
    // transitions, but NOT for seeks within the same track. Using this as
    // an additional gate in onFlush() ensures we never promote a pending
    // gain during a seek, even if endOfStreamPending was left true by the
    // decoder running ahead of the playhead before the seek was issued.
    // Set to true in onConfigure() only when endOfStreamPending is already
    // true — the exact signature of a format-changing gapless transition.
    // See full explanation in onConfigure() and onFlush() below.
    private boolean configAfterEos = false;

    public void setPendingGain(float gainDb) {
        pendingFlushGainLinear = dbToLinear(gainDb);
        hasPendingFlushGain = true;
    }

    public void setGainImmediate(float gainDb) {
        float linear = dbToLinear(gainDb);
        float prevTarget = targetGainLinear;
        float prevActive = activeGainLinear;
        targetGainLinear = linear;
        baselineGainLinear = linear;
        hasPendingFlushGain = false;
        Log.d(TAG, "setGainImmediate: " + gainDb + " dB -> linear=" + linear
                + " | was target=" + prevTarget + " active=" + prevActive
                + " baseline now=" + linear
                + " | thread=" + Thread.currentThread().getName());
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
        // Only arm the gapless-promotion flag when an end-of-stream is already
        // pending. This is the signature of a format-changing gapless transition:
        //
        //   onQueueEndOfStream()  ← decoder finished Track A (endOfStreamPending=true)
        //   onConfigure()         ← new format arrives for Track B → configAfterEos=true
        //   onFlush()             ← sink resets for new format    → promotion fires
        //
        // A post-seek onConfigure() (some DefaultAudioSink versions re-issue
        // configure() after flush()) always fires when endOfStreamPending is
        // already false (onFlush() resets it before any new configure()), so
        // configAfterEos is never set and the next flush will not promote.
        if (endOfStreamPending) {
            configAfterEos = true;
        }
        return inputAudioFormat;
    }

    @Override
    protected void onFlush() {
        Log.d(TAG, "onFlush ENTER: active=" + activeGainLinear
                + " target=" + targetGainLinear
                + " baseline=" + baselineGainLinear
                + " hasPending=" + hasPendingFlushGain
                + " pendingVal=" + pendingFlushGainLinear
                + " hasProcessed=" + hasProcessedAnyInput
                + " eosP=" + endOfStreamPending
                + " configAfterEos=" + configAfterEos
                + " ramping=" + ramping
                + " | thread=" + Thread.currentThread().getName());
        if (hasPendingFlushGain && hasProcessedAnyInput
                && endOfStreamPending && configAfterEos) {
            activeGainLinear = pendingFlushGainLinear;
            targetGainLinear = pendingFlushGainLinear;
            hasPendingFlushGain = false;
            ramping = false;
            Log.d(TAG, "onFlush: GAPLESS PROMOTION -> active/target=" + activeGainLinear);
        } else {
            Log.d(TAG, "onFlush: SEEK/STARTUP branch, restoring to baseline=" + baselineGainLinear
                    + " (was active=" + activeGainLinear + ")");
            activeGainLinear = baselineGainLinear;
            targetGainLinear = baselineGainLinear;
            ramping = false;
            hasPendingFlushGain = false;
        }
        endOfStreamPending = false;
        hasProcessedAnyInput = false;
        configAfterEos = false;
    }

    @Override
    protected void onQueueEndOfStream() {
        endOfStreamPending = true;
    }

    @Override
    protected void onReset() {
        Log.d(TAG, "onReset CALLED: active=" + activeGainLinear
                + " target=" + targetGainLinear
                + " baseline=" + baselineGainLinear
                + " | thread=" + Thread.currentThread().getName());
        activeGainLinear = baselineGainLinear;
        targetGainLinear = baselineGainLinear;
        pendingFlushGainLinear = 1.0f;
        hasPendingFlushGain = false;
        endOfStreamPending = false;
        configAfterEos = false;
        ramping = false;
        hasProcessedAnyInput = false;
        Log.d(TAG, "onReset EXIT: active/target reset to baseline=" + baselineGainLinear);
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) return;

        // Mark that real audio has flowed through the processor, so a
        // subsequent onFlush() knows it's a mid-stream boundary (safe to
        // consume pending) rather than the initial startup flush.
        hasProcessedAnyInput = true;

        // NOTE: Same-format gapless gain promotion was intentionally removed from
        // queueInput. On cached streams the decoder runs far ahead of playback,
        // so endOfStreamPending becomes true long before audio actually reaches
        // the track boundary. Triggering the promotion here caused a volume spike
        // mid-track after every seek. Gain changes at track boundaries are now
        // handled exclusively by applyGain() called from onMediaItemTransition,
        // which fires at the correct playback time.

        float target = targetGainLinear;
        if (!ramping && Math.abs(target - activeGainLinear) > 0.0001f) {
            Log.d(TAG, "queueInput: RAMP START active=" + activeGainLinear
                    + " -> target=" + target);
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
            Log.d(TAG, "advanceGain: RAMP COMPLETE -> active=" + activeGainLinear);
        }
        return gain;
    }

    private static float dbToLinear(float db) {
        db = Math.max(-60f, Math.min(15f, db));
        return (float) Math.pow(10.0, db / 20.0);
    }
}
