package com.cappielloantonio.tempo.util;

import android.media.audiofx.LoudnessEnhancer;
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

    // Maps mediaId -> [id3Gains, fallbackGains].
    // Populated proactively by prefetchQueueGains() while the previous track
    // is still playing, so the data is ready before onMediaItemTransition fires.
    // ConcurrentHashMap because prefetch callbacks arrive on background threads
    // while reads happen on the main thread.
    private static final ConcurrentHashMap<String, List<ReplayGain>> gainDataMap =
            new ConcurrentHashMap<>();

    // Tracks which mediaIds have an in-flight or completed prefetch so we
    // never submit duplicate MetadataRetriever jobs for the same item.
    private static final Set<String> prefetchedIds = ConcurrentHashMap.newKeySet();

    // Two threads: one can be fetching the next track while another fetches
    // the track after that.
    private static final ExecutorService prefetchExecutor =
            Executors.newFixedThreadPool(2);

    // LoudnessEnhancer lets us apply positive gains (above unity) that
    // player.setVolume() cannot reach — it operates directly on the audio session.
    private static LoudnessEnhancer loudnessEnhancer;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called from BaseMediaService.onAudioSessionIdChanged().
     * Re-creates the LoudnessEnhancer bound to the new audio session.
     */
    public static void attachAudioSession(int audioSessionId) {
        releaseEnhancer();
        if (audioSessionId == 0 || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return;
        try {
            loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            loudnessEnhancer.setEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "LoudnessEnhancer unavailable: " + e.getMessage());
            loudnessEnhancer = null;
        }
    }

    /** Called from BaseMediaService.onDestroy(). */
    public static void release() {
        releaseEnhancer();
        gainDataMap.clear();
        prefetchedIds.clear();
        // Do NOT shutdown the executor — it is a static pool shared across
        // service lifecycles.  Clearing the sets is enough: the next onCreate
        // will re-submit prefetch work normally.
    }

    private static void releaseEnhancer() {
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (Exception ignored) {}
            loudnessEnhancer = null;
        }
    }

    // -------------------------------------------------------------------------
    // Proactive prefetch  —  called from onTimelineChanged
    // -------------------------------------------------------------------------

    /**
     * Scans the player queue and submits a background MetadataRetriever job for
     * every item whose ReplayGain tags have not yet been fetched.  Because this
     * is called whenever the queue changes, by the time onMediaItemTransition
     * fires for track N the tags are already in gainDataMap and applyGain() can
     * apply the correct level with no audible gap or snap.
     *
     * Safe to call repeatedly — prefetchedIds deduplicates work.
     * Must be called on the main thread (Player methods require it).
     */
    public static void prefetchQueueGains(Player player) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled")) return;

        for (int i = 0; i < player.getMediaItemCount(); i++) {
            MediaItem item = player.getMediaItemAt(i);

            // Need both an ID (map key) and a resolvable URI.
            if (item.mediaId == null || item.localConfiguration == null) continue;

            // Radio streams carry live audio with no embedded ReplayGain tags;
            // submitting them to MetadataRetriever would be wasteful and wrong.
            String mediaType = item.mediaMetadata.extras != null
                    ? item.mediaMetadata.extras.getString("type") : null;
            if (Constants.MEDIA_TYPE_RADIO.equals(mediaType)) continue;
            if (item.mediaId.startsWith("ir-")) continue;

            // add() returns false when the ID is already present, meaning the
            // job is in-flight or done — skip to avoid duplicate fetches.
            if (!prefetchedIds.add(item.mediaId)) continue;

            submitPrefetch(item);
        }
    }

    private static void submitPrefetch(MediaItem item) {
        // Run the entire MetadataRetriever lifecycle — build(), get(), and close() —
        // inside the background executor.  This matches the pattern shown in the
        // media3 docs and is important for two reasons:
        //
        // 1. Crash isolation.  Any Throwable thrown here (including java.lang.Error
        //    subclasses like AssertionError or kotlin.NotImplementedError, which
        //    "catch (Exception e)" silently misses) is contained inside this thread.
        //    Nothing can propagate back to the main thread and crash the service.
        //
        // 2. Correct threading.  MetadataRetriever.Builder.build() creates its own
        //    internal HandlerThread/Looper; it is designed to be called from any
        //    thread, including a background executor thread.
        //
        // Only 2 retrievers run concurrently (pool size), which bounds memory and
        // open network connections even when the queue is large.
        prefetchExecutor.execute(() -> {
            try (MetadataRetriever retriever =
                         new MetadataRetriever.Builder(App.getInstance(), item).build()) {

                TrackGroupArray trackGroups =
                        retriever.retrieveTrackGroups().get(20,
                                java.util.concurrent.TimeUnit.SECONDS);

                List<Metadata> metadataList = extractMetadata(trackGroups);
                List<ReplayGain> gains = getReplayGains(metadataList);
                gainDataMap.put(item.mediaId, gains);
                Log.d(TAG, "Prefetched " + item.mediaId
                        + " trackGain=" + resolveTrackGain(gains));

            } catch (Throwable e) {
                // Includes InterruptedException, ExecutionException, TimeoutException,
                // Error subclasses, and anything else.  Remove the ID so a future
                // onTimelineChanged can retry (e.g. after a network reconnect).
                Log.d(TAG, "Prefetch failed for " + item.mediaId + ": " + e);
                prefetchedIds.remove(item.mediaId);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Called from onMediaItemTransition  —  primary gain-application path
    // -------------------------------------------------------------------------

    /**
     * Applies the pre-fetched ReplayGain for mediaItem immediately.
     *
     * In the normal case (prefetch completed while the previous track was
     * playing) this is a direct map lookup + volume set with no I/O.
     *
     * If prefetch hasn't finished yet (e.g. the user rapid-skipped), we apply
     * 0 dB for safety and let setReplayGain(Player, Tracks) correct the level
     * a moment later when onTracksChanged fires.
     */
    public static void applyGain(Player player, MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.mediaId == null) {
            // No item identity — can't look anything up. Leave volume unchanged;
            // onTracksChanged will apply the correct gain once tracks are known.
            Log.d(TAG, "applyGain: null mediaItem or mediaId, skipping");
            return;
        }

        List<ReplayGain> gains = gainDataMap.get(mediaItem.mediaId);
        if (gains != null) {
            // Prefetch completed before this transition — apply immediately with no gap.
            float gain = resolveGain(player, gains);
            float peak = resolvePeak(player, gains);
            Log.d(TAG, "applyGain: cache hit for " + mediaItem.mediaId
                    + " gain=" + gain + " peak=" + peak);
            setReplayGain(player, gain, peak);
        } else {
            // Prefetch hasn't arrived yet (dynamic add, rapid skip, first run, etc.).
            // Do NOT snap to 0 dB — that causes the loud transition.
            // Instead leave the volume exactly where the previous track left it.
            // onTracksChanged fires within milliseconds for gapless transitions
            // (ExoPlayer pre-buffers the next track while the current one plays),
            // and within ~1-2 s for seeks/first play. Either way, staying at the
            // previous track's level is far less jarring than a loud transient.
            Log.d(TAG, "applyGain: cache miss for " + mediaItem.mediaId
                    + ", holding current volume until onTracksChanged");
        }
    }

    // -------------------------------------------------------------------------
    // Called from onTracksChanged  —  safety net + fresh-data update
    // -------------------------------------------------------------------------

    /**
     * Safety net for the rare case where prefetch hadn't finished before
     * onMediaItemTransition fired.  In the steady state (queue was populated
     * before the track started playing) this simply re-confirms the value
     * already in gainDataMap, so there is no audible change.
     *
     * Also updates gainDataMap with whatever ExoPlayer actually parsed,
     * ensuring the map is always current.
     *
     * Ignores Tracks.EMPTY, which ExoPlayer fires during the brief loading gap
     * between gapless transitions — no tag data and no reason to touch volume.
     */
    public static void setReplayGain(Player player, Tracks tracks) {
        if (tracks == null || tracks.getGroups().isEmpty()) return;

        List<Metadata> metadataList = extractMetadata(tracks);
        List<ReplayGain> gains = getReplayGains(metadataList);

        MediaItem currentItem = player.getCurrentMediaItem();
        if (currentItem != null && currentItem.mediaId != null) {
            gainDataMap.put(currentItem.mediaId, gains);
            prefetchedIds.add(currentItem.mediaId);
        }

        setReplayGain(player, resolveGain(player, gains), resolvePeak(player, gains));
    }

    // -------------------------------------------------------------------------
    // Metadata extraction
    // -------------------------------------------------------------------------

    /** Extracts Metadata objects from a Tracks (onTracksChanged path). */
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

    /** Extracts Metadata objects from a TrackGroupArray (MetadataRetriever path). */
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
        // Consolidate into two buckets (ID3-preferred over APEv2/other):
        //   gains[0] = ID3 tags  (TextInformationFrame, InternalFrame)
        //   gains[1] = all other tags (APEv2 ApeItem, etc.)
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

    // -------------------------------------------------------------------------
    // Gain / peak resolution
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Volume / LoudnessEnhancer application
    // -------------------------------------------------------------------------

    private static void setReplayGain(Player player, float gain, float peak) {
        float preamp    = Preferences.getReplayGainPreamp();
        float totalGain = gain + preamp;

        if (Preferences.isReplayGainPreventClipping() && peak > 0f) {
            float maxGainForPeak = -(float) (20.0 * Math.log10(peak));
            if (totalGain > maxGainForPeak) totalGain = maxGainForPeak;
        }

        totalGain = Math.max(-60f, Math.min(15f, totalGain));

        // player.setVolume() is capped at 1.0; amplify via LoudnessEnhancer instead.
        if (totalGain <= 0f) {
            player.setVolume((float) Math.pow(10.0, totalGain / 20.0));
            setLoudnessEnhancerGain(0f);
        } else {
            player.setVolume(1.0f);
            setLoudnessEnhancerGain(totalGain);
        }
    }

    private static void setLoudnessEnhancerGain(float gainDb) {
        if (loudnessEnhancer == null) return;
        try {
            if (gainDb <= 0f) {
                loudnessEnhancer.setTargetGain(0);
            } else {
                loudnessEnhancer.setTargetGain((int) (gainDb * 100f));
                loudnessEnhancer.setEnabled(true);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set LoudnessEnhancer gain: " + e.getMessage());
        }
    }
}
