package com.cappielloantonio.tempo.util;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioProcessor} that applies ReplayGain adjustment directly to
 * PCM samples.
 *
 * <p>Unlike {@code player.setVolume()} or {@code LoudnessEnhancer}, this
 * processor operates <em>inside</em> ExoPlayer's audio pipeline, which means
 * gain changes are sample-accurate during gapless transitions.
 *
 * <h3>Two gain-change modes</h3>
 * <ul>
 *   <li><b>Pending gain</b> ({@link #setPendingGain}) – queued before a gapless
 *       transition and activated atomically in {@link #onFlush()}, which
 *       ExoPlayer calls at the exact track boundary.  No ramp needed because
 *       the old track's audio has already stopped.</li>
 *   <li><b>Immediate gain</b> ({@link #setGainImmediate}) – used when the
 *       transition has already happened (e.g. from {@code onMediaItemTransition}
 *       or {@code onTracksChanged}).  A short 10 ms linear ramp smooths the
 *       change so no click or pop is audible.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * {@code setPendingGain} and {@code setGainImmediate} are called from the main
 * thread; {@code queueInput} and {@code onFlush} run on the audio playback
 * thread.  All shared state uses {@code volatile} fields — 32-bit reads/writes
 * are atomic per JLS §17.7, and {@code volatile} provides the necessary
 * visibility guarantees.
 */
@OptIn(markerClass = UnstableApi.class)
public final class ReplayGainAudioProcessor extends BaseAudioProcessor {

    // ── Ramp configuration ──────────────────────────────────────────────
    private static final float RAMP_DURATION_SECONDS = 0.01f; // 10 ms

    // ── Shared state (main thread writes, audio thread reads) ───────────
    /** Target gain the audio thread should ramp toward. */
    private volatile float targetGainLinear = 1.0f;
    /** Gain to activate on the next {@link #onFlush()} (gapless pre-apply). */
    private volatile float pendingFlushGainLinear = 1.0f;
    /** Whether a pending flush gain has been set. */
    private volatile boolean hasPendingFlushGain = false;

    // ── Audio-thread-only state ─────────────────────────────────────────
    /** Gain currently being applied to samples. */
    private float activeGainLinear = 1.0f;
    /** Ramp origin. */
    private float rampFromGain = 1.0f;
    /** Ramp destination. */
    private float rampToGain = 1.0f;
    /** Number of frames over which to ramp. */
    private int rampTotalFrames = 441;        // fallback for 44.1 kHz
    /** Current position in the ramp (frames processed so far). */
    private int rampFramesDone = 0;
    /** Whether a ramp is in progress. */
    private boolean ramping = false;

    // ────────────────────────────────────────────────────────────────────
    // Public API (called from the main thread)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Queue a gain value that will activate on the next {@link #onFlush()}.
     * Use this when you know the next track's gain ahead of time (gapless
     * pre-apply).  Has <em>no</em> effect on the currently-playing audio.
     */
    public void setPendingGain(float gainDb) {
        pendingFlushGainLinear = dbToLinear(gainDb);
        hasPendingFlushGain = true;
    }

    /**
     * Change the gain right now, ramping over ~10 ms to avoid a click.
     * Use this when the track transition has already occurred.
     */
    public void setGainImmediate(float gainDb) {
        targetGainLinear = dbToLinear(gainDb);
        // Clear any stale pending gain — the transition already happened.
        hasPendingFlushGain = false;
    }

    /**
     * Discard any queued pending gain (e.g. when a seek or skip makes the
     * previously-queued value stale).
     */
    public void clearPendingGain() {
        hasPendingFlushGain = false;
    }

    // ────────────────────────────────────────────────────────────────────
    // AudioProcessor lifecycle (called on the audio playback thread)
    // ────────────────────────────────────────────────────────────────────

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws UnhandledAudioFormatException {
        int enc = inputAudioFormat.encoding;
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) {
            // We can only process these two PCM encodings.  Returning NOT_SET
            // makes the processor inactive and audio passes through unchanged.
            return AudioFormat.NOT_SET;
        }
        rampTotalFrames = Math.max(1,
                (int) (inputAudioFormat.sampleRate * RAMP_DURATION_SECONDS));
        return inputAudioFormat;   // output format == input format
    }

    @Override
    protected void onFlush() {
        // ExoPlayer calls flush() at gapless track boundaries.  If we have a
        // pending gain, activate it now — this is sample-accurate.
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
        ramping = false;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) return;

        // Check if the main thread changed the target while we were idle.
        float target = targetGainLinear;
        if (!ramping && Math.abs(target - activeGainLinear) > 0.0001f) {
            rampFromGain = activeGainLinear;
            rampToGain = target;
            rampFramesDone = 0;
            ramping = true;
        }

        // Fast path: unity gain and no ramp — just copy the buffer unchanged.
        if (!ramping && Math.abs(activeGainLinear - 1.0f) < 0.0001f) {
            ByteBuffer output = replaceOutputBuffer(remaining);
            output.put(inputBuffer);
            output.flip();
            return;
        }

        ByteBuffer output = replaceOutputBuffer(remaining);
        // Ensure correct byte order for reading/writing multi-byte samples.
        output.order(ByteOrder.nativeOrder());

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            process16Bit(inputBuffer, output);
        } else {
            processFloat(inputBuffer, output);
        }

        output.flip();
    }

    // ────────────────────────────────────────────────────────────────────
    // Per-encoding processing loops
    // ────────────────────────────────────────────────────────────────────

    private void process16Bit(ByteBuffer in, ByteBuffer out) {
        // 16-bit PCM: 2 bytes per sample.  Channels are interleaved but every
        // sample gets the same gain, so we don't need to track channels.
        while (in.remaining() >= 2) {
            float gain = advanceGain();
            short sample = in.getShort();
            float adjusted = sample * gain;
            // Clamp to 16-bit range.
            if (adjusted > Short.MAX_VALUE) adjusted = Short.MAX_VALUE;
            else if (adjusted < Short.MIN_VALUE) adjusted = Short.MIN_VALUE;
            out.putShort((short) adjusted);
        }
    }

    private void processFloat(ByteBuffer in, ByteBuffer out) {
        // 32-bit float PCM: 4 bytes per sample.
        while (in.remaining() >= 4) {
            float gain = advanceGain();
            out.putFloat(in.getFloat() * gain);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Gain helpers
    // ────────────────────────────────────────────────────────────────────

    /** Returns the gain for the current sample and advances the ramp. */
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
