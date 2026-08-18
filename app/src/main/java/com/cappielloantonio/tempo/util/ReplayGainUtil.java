package com.cappielloantonio.tempo.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.MetadataRetriever;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.extractor.metadata.id3.InternalFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.model.ReplayGain;
import com.cappielloantonio.tempo.subsonic.models.ReplayGainInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = UnstableApi.class)
public class ReplayGainUtil {
    private static final String TAG = "ReplayGainUtil";
    private static final String[] tags = {
        "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN",
        "R128_TRACK_GAIN", "R128_ALBUM_GAIN",
        "REPLAYGAIN_TRACK_PEAK", "REPLAYGAIN_ALBUM_PEAK"
    };

    private static final ConcurrentHashMap<String, List<ReplayGain>> gainDataMap =
            new ConcurrentHashMap<>();

    private static final Set<String> prefetchedIds = ConcurrentHashMap.newKeySet();

    private static final ExecutorService prefetchExecutor =
            Executors.newFixedThreadPool(2);

    // Audio processor that applies gain directly to PCM samples inside
    // ExoPlayer's audio pipeline.  Unlike player.setVolume() this is
    // sample-accurate across gapless transitions.
    private static final ReplayGainAudioProcessor audioProcessor =
            new ReplayGainAudioProcessor();

    private static volatile WeakReference<Player> playerRef = new WeakReference<>(null);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static ReplayGainAudioProcessor getAudioProcessor() {
        return audioProcessor;
    }

    public static void release() {
        gainDataMap.clear();
        prefetchedIds.clear();
        playerRef = new WeakReference<>(null);
    }

    public static void prefetchQueueGains(Player player) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled")) return;

        playerRef = new WeakReference<>(player);

        for (int i = 0; i < player.getMediaItemCount(); i++) {
            MediaItem item = player.getMediaItemAt(i);

            if (item.mediaId == null || item.localConfiguration == null) continue;

            String mediaType = item.mediaMetadata.extras != null
                    ? item.mediaMetadata.extras.getString("type") : null;
            if (Constants.MEDIA_TYPE_RADIO.equals(mediaType)) continue;
            if (item.mediaId.startsWith("ir-")) continue;

            // If the server-provided RG is already on the MediaItem, stash it
            // and skip the expensive MetadataRetriever network roundtrip.
            ReplayGainInfo serverInfo = extractServerInfo(item);
            if (serverInfo != null) {
                if (prefetchedIds.add(item.mediaId)) {
                    gainDataMap.put(item.mediaId, serverInfoToGains(serverInfo));
                    Log.d(TAG, "Prefetch skip (server RG available) " + item.mediaId);
                }
                continue;
            }

            if (!prefetchedIds.add(item.mediaId)) continue;

            submitPrefetch(item);
        }
    }

    private static void submitPrefetch(MediaItem item) {
        prefetchExecutor.execute(() -> {
            try (MetadataRetriever retriever =
                         new MetadataRetriever.Builder(App.getInstance(), item).build()) {

                TrackGroupArray trackGroups =
                        retriever.retrieveTrackGroups().get(20,
                                java.util.concurrent.TimeUnit.SECONDS);

                List<Metadata> metadataList = extractMetadata(trackGroups);
                List<ReplayGain> gains = getReplayGains(metadataList);

                // Only cache non-empty gains. If the server returned a
                // transcoded stream that strips ReplayGain tags, gains will
                // be all-zero. Storing empty gains poisons the cache: every
                // subsequent lookup (reapplyCurrentTrackGain, applyGain, etc.)
                // would find a non-null but useless entry and call
                // setGainImmediate(preamp-only = -6 dB) onto a track that was
                // already playing at the correct -18 dB, producing a spike.
                // Only update the cache when we actually got usable data.
                boolean prefetchedGainsValid = resolveTrackGain(gains) != 0f
                        || resolveAlbumGain(gains) != 0f;
                if (prefetchedGainsValid) {
                    gainDataMap.put(item.mediaId, gains);
                }
                Log.d(TAG, "Prefetched " + item.mediaId
                        + " trackGain=" + resolveTrackGain(gains)
                        + " valid=" + prefetchedGainsValid);

                // Post back to the main thread.  Two things can happen:
                //  1. If the prefetched item is the CURRENT playing track
                //     (prefetch finished AFTER the transition to it already
                //     happened, which is common on first play with a cold
                //     network), apply its gain immediately.  This corrects
                //     the audio without waiting for onTracksChanged.
                //  2. Queue the pending gain for the next gapless transition.
                mainHandler.post(() -> {
                    Player p = playerRef.get();
                    if (p == null) return;

                    MediaItem current = p.getCurrentMediaItem();
                    if (current != null && item.mediaId.equals(current.mediaId)) {
                        float gain = resolveGain(p, gains);
                        // Only apply if we have real gain data. Empty prefetch
                        // gains (gain = 0f) must not call setGainImmediate —
                        // that would write preamp-only (-6 dB) into
                        // baselineGainLinear, poisoning every future seek
                        // restore (onFlush reads baselineGainLinear) and every
                        // reapplyCurrentTrackGain call, locking the volume at
                        // the wrong level for the rest of the track.
                        if (gain != 0f) {
                            float peak = resolvePeak(p, gains);
                            float totalGain = computeTotalGain(gain, peak);
                            Log.d(TAG, "Late prefetch for current track " + item.mediaId
                                    + " — applying gain immediately totalGain=" + totalGain);
                            audioProcessor.setGainImmediate(totalGain);
                        } else {
                            Log.d(TAG, "Late prefetch for current track " + item.mediaId
                                    + " — empty gains, skipping setGainImmediate");
                        }
                    }

                    queuePendingForNextTrack(p);
                });

            } catch (Throwable e) {
                Log.d(TAG, "Prefetch failed for " + item.mediaId + ": " + e);
                prefetchedIds.remove(item.mediaId);
            }
        });
    }

    public static void applyGain(Player player, MediaItem mediaItem) {
        audioProcessor.clearPendingGain();

        if (mediaItem == null || mediaItem.mediaId == null) {
            Log.d(TAG, "applyGain: null mediaItem or mediaId, skipping");
            return;
        }

        // Fast path: OpenSubsonic RG data packed into the MediaItem extras.
        // This is always available synchronously for servers that return
        // replayGain on Child responses - no MetadataRetriever needed.
        ReplayGainInfo serverInfo = extractServerInfo(mediaItem);
        if (serverInfo != null) {
            List<ReplayGain> gains = serverInfoToGains(serverInfo);
            // Cache alongside any tag-extracted values so subsequent lookups
            // (queuePendingForNextTrack, etc.) don't re-parse the bundle.
            gainDataMap.put(mediaItem.mediaId, gains);
            prefetchedIds.add(mediaItem.mediaId);

            float gain = resolveGain(player, gains);
            float peak = resolvePeak(player, gains);
            float totalGain = computeTotalGain(gain, peak);
            Log.d(TAG, "applyGain: server RG for " + mediaItem.mediaId
                    + " gain=" + gain + " peak=" + peak
                    + " totalGain=" + totalGain);
            audioProcessor.setGainImmediate(totalGain);
            queuePendingForNextTrack(player);
            return;
        }

        // Fallback path: values extracted from file tags via MetadataRetriever.
        List<ReplayGain> gains = gainDataMap.get(mediaItem.mediaId);
        if (gains != null) {
            float gain = resolveGain(player, gains);
            if (gain != 0f) {
                float peak = resolvePeak(player, gains);
                float totalGain = computeTotalGain(gain, peak);
                Log.d(TAG, "applyGain: tag cache hit for " + mediaItem.mediaId
                        + " gain=" + gain + " peak=" + peak
                        + " totalGain=" + totalGain);
                audioProcessor.setGainImmediate(totalGain);
            } else {
                // Cache entry exists but gain is zero: the track genuinely has
                // no ReplayGain data. Apply preamp-only so this track plays at the
                // correct reference level rather than inheriting the previous track's gain.
                float preampOnly = computeTotalGain(0f, 0f);
                Log.d(TAG, "applyGain: cache hit but gain=0 for " + mediaItem.mediaId
                        + ", applying preamp-only totalGain=" + preampOnly);
                audioProcessor.setGainImmediate(preampOnly);
            }
        } else {
            Log.d(TAG, "applyGain: cache miss for " + mediaItem.mediaId
                    + ", holding current gain until onTracksChanged");
        }

        queuePendingForNextTrack(player);
    }

    public static void setReplayGain(Player player, Tracks tracks) {
        if (tracks == null || tracks.getGroups().isEmpty()) return;

        MediaItem currentItem = player.getCurrentMediaItem();

        // If the server already supplied RG for the current track, trust
        // that over tag-extracted values - the server's data is authoritative
        // (it reflects user-configured preamp, album grouping, etc.) and was
        // already applied synchronously in applyGain(). Avoid overwriting it
        // with tag-extracted values that may differ.
        if (currentItem != null && extractServerInfo(currentItem) != null) {
            Log.d(TAG, "setReplayGain: server RG already applied for "
                    + currentItem.mediaId + ", ignoring tag-extracted values");
            queuePendingForNextTrack(player);
            return;
        }

        List<Metadata> metadataList = extractMetadata(tracks);
        List<ReplayGain> gains = getReplayGains(metadataList);

        // Guard against a known media3 behaviour: after a seek on a streaming
        // MP3 (or any format where ReplayGain tags live only in the file
        // header), ExoPlayer fires onTracksChanged with a Tracks object that
        // covers the new byte-range and therefore carries no ID3/Vorbis
        // metadata.  In that case getReplayGains() returns all-zero gains and
        // setGainImmediate(0 dB) would silently undo the correct gain that was
        // applied at track start.
        //
        // If the extracted gains are empty AND we already have a valid cached
        // result for this track, keep the cached value.  The cache is populated
        // on first play (via onTracksChanged with the full header data) and by
        // the MetadataRetriever prefetch path, so it is always authoritative
        // when present.
        String mediaId = (currentItem != null) ? currentItem.mediaId : null;
        List<ReplayGain> cached = (mediaId != null) ? gainDataMap.get(mediaId) : null;
        boolean extractedIsEmpty = resolveTrackGain(gains) == 0f
                && resolveAlbumGain(gains) == 0f;
        if (extractedIsEmpty && cached != null) {
            Log.d(TAG, "setReplayGain: extracted gains empty (seek past header?), "
                    + "keeping cached gains for " + mediaId);
            gains = cached;
        } else if (mediaId != null) {
            gainDataMap.put(mediaId, gains);
            prefetchedIds.add(mediaId);
        }

        float gain = resolveGain(player, gains);

        // Guard: if we have no effective gain data (all zeros), do NOT call
        // setGainImmediate. This mirrors reapplyCurrentTrackGain's behaviour:
        // "no data — leave the current gain unchanged."
        //
        // Without this guard, an onTracksChanged that fires with empty gains
        // (e.g. seeking past the ID3 header, or a transcoded stream with no
        // tags) and no cached data would call:
        //   setGainImmediate(computeTotalGain(0f, 0f)) = preamp = -6 dB
        // onto a track playing at e.g. -18 dB, causing a +12 dB spike.
        //
        // Tracks with a genuine 0 dB album/track gain are correctly handled
        // by applyGain() (called from onMediaItemTransition), which already
        // applied the correct preamp-only value when the track started.
        if (gain == 0f) {
            // No RG data for this track. Apply preamp-only so the track plays at
            // the correct reference level rather than inheriting whatever gain the
            // previous track left behind.
            float preampOnly = computeTotalGain(0f, 0f);
            Log.d(TAG, "setReplayGain: no effective gain data for " + mediaId
                    + ", applying preamp-only totalGain=" + preampOnly);
            audioProcessor.setGainImmediate(preampOnly);
            queuePendingForNextTrack(player);
            return;
        }

        float peak = resolvePeak(player, gains);
        audioProcessor.setGainImmediate(computeTotalGain(gain, peak));

        queuePendingForNextTrack(player);
    }

    /**
     * Re-asserts the correct gain for the track that is currently playing.
     *
     * <p>Called from {@code onPositionDiscontinuity} whenever the user seeks
     * within the same track.  The problem it solves: ExoPlayer's decoder can
     * run ahead of the playhead, causing {@code onQueueEndOfStream()} to fire
     * before the seek is even issued.  That leaves
     * {@code endOfStreamPending = true} in the audio processor.  When the seek
     * then triggers {@code onFlush()}, the processor sees all three
     * preconditions satisfied ({@code hasPendingFlushGain &amp;&amp;
     * hasProcessedAnyInput &amp;&amp; endOfStreamPending}) and incorrectly
     * promotes the <em>next</em> track's pending gain onto the current track.
     * If that pending gain is the fallback 0 dB value (no RG data cached for
     * the next track yet), the audio jumps to unity gain — the dramatic volume
     * increase the user hears.
     *
     * <p>This method is intentionally narrow: it only calls
     * {@link ReplayGainAudioProcessor#setGainImmediate} to restore the right
     * level; it does <em>not</em> touch the pending gain for the next track
     * (already queued correctly) and does <em>not</em> call
     * {@link #queuePendingForNextTrack} again.
     */
    public static void reapplyCurrentTrackGain(Player player) {
        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem == null || currentItem.mediaId == null) {
            Log.d(TAG, "reapplyCurrentTrackGain: no current item, skipping");
            return;
        }

        // Fast path: server-supplied RG on the MediaItem (OpenSubsonic).
        ReplayGainInfo serverInfo = extractServerInfo(currentItem);
        if (serverInfo != null) {
            List<ReplayGain> gains = serverInfoToGains(serverInfo);
            float gain = resolveGain(player, gains);
            float peak = resolvePeak(player, gains);
            float totalGain = computeTotalGain(gain, peak);
            Log.d(TAG, "reapplyCurrentTrackGain: server RG for " + currentItem.mediaId
                    + " totalGain=" + totalGain);
            audioProcessor.setGainImmediate(totalGain);
            return;
        }

        // Fallback: tag-based gains from the in-memory cache.
        List<ReplayGain> cached = gainDataMap.get(currentItem.mediaId);
        if (cached != null) {
            float gain = resolveGain(player, cached);
            if (gain != 0f) {
                float peak = resolvePeak(player, cached);
                float totalGain = computeTotalGain(gain, peak);
                Log.d(TAG, "reapplyCurrentTrackGain: cache hit for " + currentItem.mediaId
                        + " totalGain=" + totalGain);
                audioProcessor.setGainImmediate(totalGain);
                return;
            }
            // Cache entry exists but gain is zero (empty/poisoned). Fall through
            // to the "no data" case and keep the current gain unchanged.
            Log.d(TAG, "reapplyCurrentTrackGain: cache hit but gain=0 for "
                    + currentItem.mediaId + ", keeping current gain");
            return;
        }

        // No data available yet — leave the current gain unchanged rather than
        // snapping to an arbitrary value.  onTracksChanged / the late-prefetch
        // callback will apply the correct gain once data arrives.
        Log.d(TAG, "reapplyCurrentTrackGain: no cached data for "
                + currentItem.mediaId + ", keeping current gain");
    }

    private static void queuePendingForNextTrack(Player player) {
        int nextIndex = player.getNextMediaItemIndex();
        if (nextIndex == C.INDEX_UNSET) return;
        MediaItem nextItem = player.getMediaItemAt(nextIndex);
        if (nextItem == null || nextItem.mediaId == null) return;

        List<ReplayGain> gains = gainDataMap.get(nextItem.mediaId);
        float resolvedGain = (gains != null) ? resolveGainForNextTrack(player, gains) : 0f;

        if (resolvedGain == 0f) {
            if (gains == null) {
                // No data cached yet — data may arrive via prefetch or onTracksChanged.
                // Carry over the current track's gain so there is no sudden change at
                // the boundary; the correct value will be applied once data arrives.
                Log.d(TAG, "queuePendingForNextTrack: no RG data yet for "
                        + nextItem.mediaId + ", carrying over current gain");
                return;
            }
            // gains != null but resolvedGain == 0: we have confirmed the next track
            // has no ReplayGain data. Queue preamp-only so the gapless transition
            // applies the correct baseline instead of inheriting the current track's
            // gain level.
            float preampOnly = computeTotalGain(0f, 0f);
            audioProcessor.setPendingGain(preampOnly);
            Log.d(TAG, "queuePendingForNextTrack: no RG tags for "
                    + nextItem.mediaId + ", queuing preamp-only totalGain=" + preampOnly);
            return;
        }

        float totalGain = computeTotalGain(resolvedGain, resolvePeakForNextTrack(player, gains));
        audioProcessor.setPendingGain(totalGain);
    }

    private static List<Metadata> extractMetadata(Tracks tracks) {
        List<Metadata> result = new ArrayList<>();
        if (tracks == null) return result;
        for (int i = 0; i < tracks.getGroups().size(); i++) {
            Tracks.Group group = tracks.getGroups().get(i);
            if (group == null || group.getMediaTrackGroup() == null) continue;
            for (int j = 0; j < group.getMediaTrackGroup().length; j++) {
                Metadata m = group.getTrackFormat(j).metadata;
                if (m != null) result.add(m);
            }
        }
        return result;
    }

    private static List<Metadata> extractMetadata(TrackGroupArray trackGroups) {
        List<Metadata> result = new ArrayList<>();
        if (trackGroups == null) return result;
        for (int i = 0; i < trackGroups.length; i++) {
            TrackGroup group = trackGroups.get(i);
            if (group == null) continue;
            for (int j = 0; j < group.length; j++) {
                Metadata m = group.getFormat(j).metadata;
                if (m != null) result.add(m);
            }
        }
        return result;
    }

    private static List<ReplayGain> getReplayGains(List<Metadata> metadataList) {
        ReplayGain id3Gains      = new ReplayGain();
        ReplayGain fallbackGains = new ReplayGain();

        if (metadataList != null) {
            for (Metadata metadata : metadataList) {
                if (metadata == null) continue;
                for (int j = 0; j < metadata.length(); j++) {
                    Metadata.Entry entry = metadata.get(j);
                    if (!isReplayGainEntry(entry)) continue;
                    boolean isId3 = (entry instanceof TextInformationFrame)
                                 || (entry instanceof InternalFrame);
                    mergeIntoReplayGain(entry, isId3 ? id3Gains : fallbackGains);
                }
            }
        }

        List<ReplayGain> gains = new ArrayList<>();
        gains.add(id3Gains);
        gains.add(fallbackGains);
        return gains;
    }

    private static boolean isReplayGainEntry(Metadata.Entry entry) {
        String upper = entry.toString().toUpperCase(java.util.Locale.ROOT);
        for (String tag : tags) {
            if (upper.contains(tag)) return true;
        }
        return false;
    }

    private static void mergeIntoReplayGain(Metadata.Entry entry, ReplayGain target) {
        String str = entry.toString();
        if (entry instanceof InternalFrame) {
            str = ((InternalFrame) entry).description + ((InternalFrame) entry).text;
        } else if (entry instanceof TextInformationFrame) {
            TextInformationFrame tf = (TextInformationFrame) entry;
            String desc = tf.description != null ? tf.description : tf.id;
            str = desc + (!tf.values.isEmpty() ? tf.values.get(0) : "");
        }

        String upper = str.toUpperCase(java.util.Locale.ROOT);

        if (upper.contains(tags[0])) target.setTrackGain(parseReplayGainTag(str));
        if (upper.contains(tags[1])) target.setAlbumGain(parseReplayGainTag(str));
        if (upper.contains(tags[2])) target.setTrackGain(parseReplayGainTag(str) / 256f + 5f);
        if (upper.contains(tags[3])) target.setAlbumGain(parseReplayGainTag(str) / 256f + 5f);
        if (upper.contains(tags[4])) target.setTrackPeak(parseReplayGainTag(str));
        if (upper.contains(tags[5])) target.setAlbumPeak(parseReplayGainTag(str));
    }

    private static float parseReplayGainTag(String entry) {
        try {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile(
                            "(-?\\d+(?:\\.\\d+)?)\\s*(?:dB)?\\s*$",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(entry.trim());
            String lastMatch = null;
            while (matcher.find()) lastMatch = matcher.group(1);
            return lastMatch != null ? Float.parseFloat(lastMatch) : 0f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static float resolveGain(Player player, List<ReplayGain> gains) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled")
                || gains == null || gains.isEmpty()) return 0f;

        String mode = Objects.toString(Preferences.getReplayGainMode(), "");
        switch (mode) {
            case "track": return resolveTrackGain(gains);
            case "album": return resolveAlbumGain(gains);
            case "auto":  return areTracksConsecutive(player)
                                 ? resolveAlbumGain(gains) : resolveTrackGain(gains);
            default:      return 0f;
        }
    }

    private static float resolveGainForNextTrack(Player player, List<ReplayGain> gains) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled")
                || gains == null || gains.isEmpty()) return 0f;

        String mode = Objects.toString(Preferences.getReplayGainMode(), "");
        switch (mode) {
            case "track": return resolveTrackGain(gains);
            case "album": return resolveAlbumGain(gains);
            case "auto":  return areCurrentAndNextConsecutive(player)
                                 ? resolveAlbumGain(gains) : resolveTrackGain(gains);
            default:      return 0f;
        }
    }

    private static float resolveTrackGain(List<ReplayGain> gains) {
        float primary   = gains.get(0).getTrackGain();
        float secondary = gains.get(1).getTrackGain();
        return primary != 0f ? primary : secondary;
    }

    private static float resolveAlbumGain(List<ReplayGain> gains) {
        float primary   = gains.get(0).getAlbumGain();
        float secondary = gains.get(1).getAlbumGain();
        float album = primary != 0f ? primary : secondary;
        return album != 0f ? album : resolveTrackGain(gains);
    }

    private static float resolvePeak(Player player, List<ReplayGain> gains) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled")
                || gains == null || gains.isEmpty()) return 0f;

        boolean useAlbum = Objects.equals(Preferences.getReplayGainMode(), "album")
                || (Objects.equals(Preferences.getReplayGainMode(), "auto")
                    && areTracksConsecutive(player));

        return resolveTrackOrAlbumPeak(gains, useAlbum);
    }

    private static float resolvePeakForNextTrack(Player player, List<ReplayGain> gains) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled")
                || gains == null || gains.isEmpty()) return 0f;

        boolean useAlbum = Objects.equals(Preferences.getReplayGainMode(), "album")
                || (Objects.equals(Preferences.getReplayGainMode(), "auto")
                    && areCurrentAndNextConsecutive(player));

        return resolveTrackOrAlbumPeak(gains, useAlbum);
    }

    private static float resolveTrackOrAlbumPeak(List<ReplayGain> gains, boolean useAlbum) {
        if (useAlbum) {
            float primary   = gains.get(0).getAlbumPeak();
            float secondary = gains.get(1).getAlbumPeak();
            float albumPeak = primary != 0f ? primary : secondary;
            if (albumPeak != 0f) return albumPeak;
        }

        float primary   = gains.get(0).getTrackPeak();
        float secondary = gains.get(1).getTrackPeak();
        return primary != 0f ? primary : secondary;
    }

    /** Checks if the current and previous tracks share the same album. */
    private static boolean areTracksConsecutive(Player player) {
        MediaItem current = player.getCurrentMediaItem();
        int prevIdx = player.getPreviousMediaItemIndex();
        MediaItem prev = prevIdx == C.INDEX_UNSET ? null : player.getMediaItemAt(prevIdx);
        return current != null && prev != null
                && current.mediaMetadata.albumTitle != null
                && prev.mediaMetadata.albumTitle != null
                && prev.mediaMetadata.albumTitle.toString()
                       .equals(current.mediaMetadata.albumTitle.toString());
    }

    /** Checks if the current and NEXT tracks share the same album. */
    private static boolean areCurrentAndNextConsecutive(Player player) {
        MediaItem current = player.getCurrentMediaItem();
        int nextIdx = player.getNextMediaItemIndex();
        MediaItem next = nextIdx == C.INDEX_UNSET ? null : player.getMediaItemAt(nextIdx);
        return current != null && next != null
                && current.mediaMetadata.albumTitle != null
                && next.mediaMetadata.albumTitle != null
                && current.mediaMetadata.albumTitle.toString()
                       .equals(next.mediaMetadata.albumTitle.toString());
    }

    // Total gain computation (preamp + clipping prevention)

    private static float computeTotalGain(float gain, float peak) {
        float preamp    = Preferences.getLoudnessPreamp();
        float totalGain = gain + preamp;

        if (Preferences.isReplayGainPreventClipping() && peak > 0f) {
            float maxGainForPeak = -(float) (20.0 * Math.log10(peak));
            if (totalGain > maxGainForPeak) totalGain = maxGainForPeak;
        }

        return Math.max(-60f, Math.min(15f, totalGain));
    }

    /**
     * Reads OpenSubsonic ReplayGain info from the MediaItem's extras bundle
     * (populated at mapping time from the `replayGain` object on the
     * server's Child response). Returns null if the server didn't provide
     * data, if the data carries no meaningful values, or if the bundle is
     * missing.
     */
    private static ReplayGainInfo extractServerInfo(MediaItem item) {
        if (item == null || item.mediaMetadata == null) return null;
        if (!ReplayGainBundleUtil.isPresent(item.mediaMetadata.extras)) return null;
        ReplayGainInfo info = ReplayGainBundleUtil.fromBundle(item.mediaMetadata.extras);
        return (info != null && info.hasAnyValue()) ? info : null;
    }

    private static List<ReplayGain> serverInfoToGains(ReplayGainInfo info) {
        ReplayGain primary = new ReplayGain();
        if (info.getTrackGain() != null) primary.setTrackGain(info.getTrackGain());
        if (info.getAlbumGain() != null) primary.setAlbumGain(info.getAlbumGain());
        if (info.getTrackPeak() != null) primary.setTrackPeak(info.getTrackPeak());
        if (info.getAlbumPeak() != null) primary.setAlbumPeak(info.getAlbumPeak());

        ReplayGain secondary = new ReplayGain();
        Float fallback = info.getFallbackGain();
        if (fallback != null) {
            secondary.setTrackGain(fallback);
            secondary.setAlbumGain(fallback);
        }

        List<ReplayGain> gains = new ArrayList<>();
        gains.add(primary);
        gains.add(secondary);
        return gains;
    }
}
