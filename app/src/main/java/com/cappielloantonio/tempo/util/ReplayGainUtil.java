package com.cappielloantonio.tempo.util;

import android.media.audiofx.LoudnessEnhancer;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.Player;
import androidx.media3.extractor.metadata.id3.InternalFrame;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;

import com.cappielloantonio.tempo.model.ReplayGain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@OptIn(markerClass = UnstableApi.class)
public class ReplayGainUtil {
    private static final String TAG = "ReplayGainUtil";
    private static final String[] tags = {
        "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN",
        "R128_TRACK_GAIN", "R128_ALBUM_GAIN",
        "REPLAYGAIN_TRACK_PEAK", "REPLAYGAIN_ALBUM_PEAK"
    };
    private static final Map<String, Float> gainCache = new HashMap<>();
    private static final Map<String, Float> peakCache = new HashMap<>();

    // LoudnessEnhancer lets us apply positive gains (above unity) that
    // player.setVolume() cannot reach — it operates directly on the audio session.
    private static LoudnessEnhancer loudnessEnhancer;

    /**
     * Called from BaseMediaService.onAudioSessionIdChanged().
     * Re-creates the LoudnessEnhancer bound to the new session.
     */
    public static void attachAudioSession(int audioSessionId) {
        releaseEnhancer();
        if (audioSessionId == 0 || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return;
        try {
            loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            loudnessEnhancer.setEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "LoudnessEnhancer unavailable on this device: " + e.getMessage());
            loudnessEnhancer = null;
        }
    }

    /** Called from BaseMediaService.onDestroy(). */
    public static void release() {
        releaseEnhancer();
        gainCache.clear();
        peakCache.clear();
    }

    private static void releaseEnhancer() {
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (Exception ignored) {}
            loudnessEnhancer = null;
        }
    }

    public static void setReplayGain(Player player, Tracks tracks) {
        List<Metadata> metadata = getMetadata(tracks);
        List<ReplayGain> gains = getReplayGains(metadata);

        MediaItem currentMediaItem = player.getCurrentMediaItem();
        float gain = resolveGain(player, gains);
        float peak = resolvePeak(player, gains);

        if (currentMediaItem != null && currentMediaItem.mediaId != null) {
            gainCache.put(currentMediaItem.mediaId, gain);
            peakCache.put(currentMediaItem.mediaId, peak);
        }

        setReplayGain(player, gain, peak);
    }

    public static void applyCachedReplayGain(Player player, MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.mediaId == null) {
            setReplayGain(player, 0f, 0f);
            return;
        }

        Float cachedGain = gainCache.get(mediaItem.mediaId);
        Float cachedPeak = peakCache.get(mediaItem.mediaId);
        setReplayGain(player, cachedGain != null ? cachedGain : 0f,
                             cachedPeak != null ? cachedPeak : 0f);
    }

    private static List<Metadata> getMetadata(Tracks tracks) {
        List<Metadata> metadata = new ArrayList<>();

        if (tracks != null && !tracks.getGroups().isEmpty()) {
            for (int i = 0; i < tracks.getGroups().size(); i++) {
                Tracks.Group group = tracks.getGroups().get(i);

                if (group != null && group.getMediaTrackGroup() != null) {
                    for (int j = 0; j < group.getMediaTrackGroup().length; j++) {
                        metadata.add(group.getTrackFormat(j).metadata);
                    }
                }
            }
        }

        return metadata;
    }

    private static List<ReplayGain> getReplayGains(List<Metadata> metadataList) {
        // Consolidate all entries into exactly two buckets, preserving the
        // existing resolveTrackGain / resolveAlbumGain fallback contract:
        //   gains[0] = ID3 tags  (TextInformationFrame, InternalFrame) — preferred
        //   gains[1] = all other tags (APEv2 ApeItem, etc.)            — fallback
        //
        // This fixes two bugs that occur when a file has both ID3 and APEv2 tags:
        //   1. Each entry used to produce a separate ReplayGain object, so
        //      resolveTrackGain (which only looks at [0] and [1]) would miss
        //      the actual gain value when album tags sort before track tags.
        //   2. There was no priority rule, so APEv2 could silently win over ID3.
        ReplayGain id3Gains     = new ReplayGain();
        ReplayGain fallbackGains = new ReplayGain();

        if (metadataList != null) {
            for (int i = 0; i < metadataList.size(); i++) {
                Metadata metadata = metadataList.get(i);
                if (metadata == null) continue;
                for (int j = 0; j < metadata.length(); j++) {
                    Metadata.Entry entry = metadata.get(j);
                    if (!checkReplayGain(entry)) continue;
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

    /**
     * Parses a single metadata entry and merges any ReplayGain values it contains
     * into the provided ReplayGain object (last write wins within a bucket, but
     * in practice each tag appears only once per tag format).
     */
    private static void mergeIntoReplayGain(Metadata.Entry entry, ReplayGain target) {
        String str = entry.toString();
        if (entry instanceof InternalFrame) {
            str = ((InternalFrame) entry).description + ((InternalFrame) entry).text;
        } else if (entry instanceof TextInformationFrame) {
            TextInformationFrame textFrame = (TextInformationFrame) entry;
            String desc = textFrame.description != null ? textFrame.description : textFrame.id;
            str = desc + (!textFrame.values.isEmpty() ? textFrame.values.get(0) : "");
        }

        String strUpper = str.toUpperCase(java.util.Locale.ROOT);

        if (strUpper.contains(tags[0])) target.setTrackGain(parseReplayGainTag(str));
        if (strUpper.contains(tags[1])) target.setAlbumGain(parseReplayGainTag(str));
        if (strUpper.contains(tags[2])) target.setTrackGain(parseReplayGainTag(str) / 256f + 5f);
        if (strUpper.contains(tags[3])) target.setAlbumGain(parseReplayGainTag(str) / 256f + 5f);
        if (strUpper.contains(tags[4])) target.setTrackPeak(parseReplayGainTag(str));
        if (strUpper.contains(tags[5])) target.setAlbumPeak(parseReplayGainTag(str));
    }

    private static Float parseReplayGainTag(String entry) {
        try {
            // Find the last floating-point number in the string (the actual gain value)
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*(?:dB)?\\s*$",
                                    java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(entry.trim());

            String lastMatch = null;
            while (matcher.find()) {
                lastMatch = matcher.group(1);
            }

            return lastMatch != null ? Float.parseFloat(lastMatch) : 0f;
        } catch (NumberFormatException exception) {
            return 0f;
        }
    }

    private static float resolveGain(Player player, List<ReplayGain> gains) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled") || gains == null || gains.isEmpty()) {
            return 0f;
        }

        if (Objects.equals(Preferences.getReplayGainMode(), "track")) {
            return resolveTrackGain(gains);
        }

        if (Objects.equals(Preferences.getReplayGainMode(), "album")) {
            return resolveAlbumGain(gains);
        }

        if (Objects.equals(Preferences.getReplayGainMode(), "auto")) {
            return areTracksConsecutive(player) ? resolveAlbumGain(gains) : resolveTrackGain(gains);
        }

        return 0f;
    }

    private static float resolveTrackGain(List<ReplayGain> gains) {
        float primaryTrackGain = gains.get(0).getTrackGain();
        float secondaryTrackGain = gains.get(1).getTrackGain();
        return primaryTrackGain != 0f ? primaryTrackGain : secondaryTrackGain;
    }

    private static float resolveAlbumGain(List<ReplayGain> gains) {
        float primaryAlbumGain = gains.get(0).getAlbumGain();
        float secondaryAlbumGain = gains.get(1).getAlbumGain();
        float albumGain = primaryAlbumGain != 0f ? primaryAlbumGain : secondaryAlbumGain;
        // Fall back to track gain when album gain is absent
        return albumGain != 0f ? albumGain : resolveTrackGain(gains);
    }

    private static float resolvePeak(Player player, List<ReplayGain> gains) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled") || gains == null || gains.isEmpty()) {
            return 0f;
        }

        boolean useAlbum = Objects.equals(Preferences.getReplayGainMode(), "album") ||
                (Objects.equals(Preferences.getReplayGainMode(), "auto") && areTracksConsecutive(player));

        if (useAlbum) {
            float primary = gains.get(0).getAlbumPeak();
            float secondary = gains.get(1).getAlbumPeak();
            float albumPeak = primary != 0f ? primary : secondary;
            // Fall back to track peak when album peak is absent
            if (albumPeak != 0f) return albumPeak;
        }

        // Track peak (also the fallback for album mode without album peak)
        float primary = gains.get(0).getTrackPeak();
        float secondary = gains.get(1).getTrackPeak();
        return primary != 0f ? primary : secondary;
    }

    private static boolean areTracksConsecutive(Player player) {
        MediaItem currentMediaItem = player.getCurrentMediaItem();
        int prevMediaItemIndex = player.getPreviousMediaItemIndex();
        MediaItem pastMediaItem = prevMediaItemIndex == C.INDEX_UNSET ? null : player.getMediaItemAt(prevMediaItemIndex);

        return currentMediaItem != null &&
                pastMediaItem != null &&
                pastMediaItem.mediaMetadata.albumTitle != null &&
                currentMediaItem.mediaMetadata.albumTitle != null &&
                pastMediaItem.mediaMetadata.albumTitle.toString().equals(currentMediaItem.mediaMetadata.albumTitle.toString());
    }

    private static void setReplayGain(Player player, float gain, float peak) {
        float preamp = Preferences.getReplayGainPreamp();
        float totalGain = gain + preamp;

        // Prevent clipping: if a peak value is available and the setting is enabled,
        // cap the total gain so that (peak * 10^(totalGain/20)) never exceeds 1.0 (0 dBFS).
        if (Preferences.isReplayGainPreventClipping() && peak > 0f) {
            // Maximum gain that keeps the peak at exactly 0 dBFS: -20 * log10(peak)
            float maxGainForPeak = -(float) (20.0 * Math.log10(peak));
            if (totalGain > maxGainForPeak) {
                totalGain = maxGainForPeak;
            }
        }

        totalGain = Math.max(-60f, Math.min(15f, totalGain));

        // player.setVolume() is limited to [0.0, 1.0] — it cannot amplify above unity.
        // Split the gain across two mechanisms:
        //   - Negative / zero gain: pure attenuation via player.setVolume(), LoudnessEnhancer = 0
        //   - Positive gain:        player.setVolume(1.0), boost via LoudnessEnhancer in millibels
        if (totalGain <= 0f) {
            player.setVolume((float) Math.pow(10f, totalGain / 20f));
            setLoudnessEnhancerGain(0f);
        } else {
            player.setVolume(1.0f);
            setLoudnessEnhancerGain(totalGain);
        }
    }

    /**
     * Applies a positive gain (dB) through LoudnessEnhancer, or zeros it out.
     * LoudnessEnhancer.setTargetGain() takes millibels (1 dB = 100 mB).
     */
    private static void setLoudnessEnhancerGain(float gainDb) {
        if (loudnessEnhancer == null) return;
        try {
            if (gainDb <= 0f) {
                loudnessEnhancer.setTargetGain(0);
                // Leave enabled so it's ready for the next track; zero gain is transparent.
            } else {
                loudnessEnhancer.setTargetGain((int) (gainDb * 100f));
                loudnessEnhancer.setEnabled(true);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set LoudnessEnhancer gain: " + e.getMessage());
        }
    }
}
