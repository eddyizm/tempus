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
                gainDataMap.put(item.mediaId, gains);
                Log.d(TAG, "Prefetched " + item.mediaId
                        + " trackGain=" + resolveTrackGain(gains));

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
                        float peak = resolvePeak(p, gains);
                        float totalGain = computeTotalGain(gain, peak);
                        Log.d(TAG, "Late prefetch for current track " + item.mediaId
                                + " — applying gain immediately totalGain=" + totalGain);
                        audioProcessor.setGainImmediate(totalGain);
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
        // replayGain on Child responses — no MetadataRetriever needed.
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
            float peak = resolvePeak(player, gains);
            float totalGain = computeTotalGain(gain, peak);
            Log.d(TAG, "applyGain: tag cache hit for " + mediaItem.mediaId
                    + " gain=" + gain + " peak=" + peak
                    + " totalGain=" + totalGain);
            audioProcessor.setGainImmediate(totalGain);
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
        // that over tag-extracted values — the server's data is authoritative
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

        if (currentItem != null && currentItem.mediaId != null) {
            gainDataMap.put(currentItem.mediaId, gains);
            prefetchedIds.add(currentItem.mediaId);
        }

        float gain = resolveGain(player, gains);
        float peak = resolvePeak(player, gains);
        audioProcessor.setGainImmediate(computeTotalGain(gain, peak));

        queuePendingForNextTrack(player);
    }

    private static void queuePendingForNextTrack(Player player) {
        int nextIndex = player.getNextMediaItemIndex();
        if (nextIndex == C.INDEX_UNSET) return;
        MediaItem nextItem = player.getMediaItemAt(nextIndex);
        if (nextItem == null || nextItem.mediaId == null) return;

        List<ReplayGain> gains = gainDataMap.get(nextItem.mediaId);
        if (gains == null) {
            // We have no RG data for the next track yet (its prefetch may
            // still be in flight, or it may genuinely have no tags). Without
            // a pending gain, onQueueEndOfStream leaves activeGain at the
            // current track's value, so the next track's opening samples
            // would play at the previous track's gain — very audible when
            // the current track has a large positive gain. Queue a neutral
            // fallback (preamp only) so the gapless handoff lands on a
            // conservative volume; if tag extraction later resolves a real
            // value, onTracksChanged / the late-prefetch callback will apply
            // it with a short ramp.
            float fallback = computeTotalGain(0f, 0f);
            audioProcessor.setPendingGain(fallback);
            Log.d(TAG, "queuePendingForNextTrack: no data for "
                    + nextItem.mediaId + ", queuing fallback gain=" + fallback);
            return;
        }

        float totalGain = computeTotalGain(
                resolveGainForNextTrack(player, gains),
                resolvePeakForNextTrack(player, gains));
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
        float preamp    = Preferences.getReplayGainPreamp();
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

    /**
     * Adapts an OpenSubsonic {@link ReplayGainInfo} onto the internal
     * {@code List<ReplayGain>} shape produced by tag extraction.
     *
     * The existing code treats the list as [id3Gains, fallbackGains], with
     * {@link #resolveTrackGain} / {@link #resolveAlbumGain} preferring the
     * first non-zero entry. We put server data in the primary slot and
     * expose the server's fallback_gain in the secondary slot so that if
     * track/album gain is unavailable, fallback_gain still applies.
     *
     * Note: OpenSubsonic's `fallbackGain` is documented as a value to use
     * when neither track nor album gain is present, so mirroring it into
     * both track and album gain of the secondary entry gives the right
     * behaviour under any resolve mode.
     */
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
