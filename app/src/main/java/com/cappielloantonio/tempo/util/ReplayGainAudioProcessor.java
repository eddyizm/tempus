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

    // Tracks whether any samples have been processed since the last flush.
    // onFlush() is called both at initial audio-sink configuration (before
    // any audio has played) AND at mid-stream format-change transitions.
    // We only want to consume the pending gain in the second case — at
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
        // Promote the pending (next-track) gain only when this flush is the
        // result of a genuine format-changing gapless track transition.
        //
        // The three existing guards (hasPendingFlushGain, hasProcessedAnyInput,
        // endOfStreamPending) are necessary but not sufficient: ExoPlayer's
        // decoder often runs ahead of the playhead, so onQueueEndOfStream()
        // can fire before a user-initiated seek. When the seek then triggers
        // onFlush() the processor incorrectly sees all three gates as true and
        // promotes the next track's (or fallback) gain onto the current track.
        //
        // configAfterEos is the decisive fourth gate.  It is set in
        // onConfigure() only when endOfStreamPending is already true at that
        // moment (i.e. a real EOS immediately precedes a format change).
        // A post-seek onConfigure() always arrives after onFlush() has already
        // reset endOfStreamPending to false, so it can never arm configAfterEos
        // for that flush or any subsequent seek-flush.
        if (hasPendingFlushGain && hasProcessedAnyInput
                && endOfStreamPending && configAfterEos) {
            activeGainLinear = pendingFlushGainLinear;
            targetGainLinear = pendingFlushGainLinear;
            hasPendingFlushGain = false;
            ramping = false;
        } else {
            // Seek (or initial startup flush): snap targetGainLinear to the
            // current activeGainLinear so that any ramp that was in flight
            // (e.g. a queueInput promotion that fired because the decoder had
            // run ahead) is cancelled immediately.  Without this, post-seek
            // audio would continue ramping toward the pre-seek target and the
            // volume would drift to the wrong level before reapplyCurrentTrackGain
            // on the main thread gets a chance to correct things.
            targetGainLinear = activeGainLinear;
            ramping = false;
            // Clear the stale pending gain so that queueInput's same-format
            // gapless promotion cannot fire after the seek.  After the seek,
            // the decoder runs ahead again and fires onQueueEndOfStream() a
            // second time (endOfStreamPending = true).  With hasPendingFlushGain
            // still true, queueInput would see both flags set and immediately
            // promote the stale pending gain (a 0 dB fallback or the next
            // track's gain) onto the current track — the volume spike the user
            // hears.  Clearing it here prevents that.  The correct next-track
            // gain is re-queued shortly after by setReplayGain →
            // queuePendingForNextTrack() when onTracksChanged fires post-seek.
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
        activeGainLinear = baselineGainLinear;
        targetGainLinear = baselineGainLinear;
        pendingFlushGainLinear = 1.0f;
        hasPendingFlushGain = false;
        endOfStreamPending = false;
        configAfterEos = false;
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

        // Same-format gapless transition: onQueueEndOfStream() fired (decoder
        // finished Track A), but DefaultAudioSink skipped onFlush() because
        // the format is unchanged. The first samples arriving here belong to
        // Track B — promote the pending gain now so the transition is correct.
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
