package com.cappielloantonio.tempo.util;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.Player;
import androidx.media3.extractor.metadata.id3.InternalFrame;

import com.cappielloantonio.tempo.model.ReplayGain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@OptIn(markerClass = UnstableApi.class)
public class ReplayGainUtil {
    private static final String[] tags = {"REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN", "R128_TRACK_GAIN", "R128_ALBUM_GAIN"};
    private static final Map<String, Float> gainCache = new HashMap<>();

    public static void setReplayGain(Player player, Tracks tracks) {
        List<Metadata> metadata = getMetadata(tracks);
        List<ReplayGain> gains = getReplayGains(metadata);

        MediaItem currentMediaItem = player.getCurrentMediaItem();
        float gain = resolveGain(player, gains);

        if (currentMediaItem != null && currentMediaItem.mediaId != null) {
            gainCache.put(currentMediaItem.mediaId, gain);
        }

        setReplayGain(player, gain);
    }

    public static void applyCachedReplayGain(Player player, MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.mediaId == null) {
            setReplayGain(player, 0f);
            return;
        }

        Float cachedGain = gainCache.get(mediaItem.mediaId);
        setReplayGain(player, cachedGain != null ? cachedGain : 0f);
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

    private static List<ReplayGain> getReplayGains(List<Metadata> metadata) {
        List<ReplayGain> gains = new ArrayList<>();

        if (metadata != null) {
            for (int i = 0; i < metadata.size(); i++) {
                Metadata singleMetadata = metadata.get(i);

                if (singleMetadata != null) {
                    for (int j = 0; j < singleMetadata.length(); j++) {
                        Metadata.Entry entry = singleMetadata.get(j);

                        if (checkReplayGain(entry)) {
                            ReplayGain replayGain = setReplayGains(entry);
                            gains.add(replayGain);
                        }
                    }
                }
            }
        }

        if (gains.isEmpty()) gains.add(0, new ReplayGain());
        if (gains.size() == 1) gains.add(1, new ReplayGain());

        return gains;
    }

    private static boolean checkReplayGain(Metadata.Entry entry) {
        String entryStr = entry.toString().toUpperCase(java.util.Locale.ROOT);
        for (String tag : tags) {
            if (entryStr.contains(tag)) {
                return true;
            }
        }

        return false;
    }

    private static ReplayGain setReplayGains(Metadata.Entry entry) {
        ReplayGain replayGain = new ReplayGain();

        // The logic below assumes .toString() contains the dB value. That's not the case for InternalFrame
        String str = entry.toString();
        if (entry instanceof InternalFrame) {
            str = ((InternalFrame) entry).description + ((InternalFrame) entry).text;
        }

        String strUpper = str.toUpperCase(java.util.Locale.ROOT);
        
        if (strUpper.contains(tags[0])) {
            replayGain.setTrackGain(parseReplayGainTag(str));
        }

        if (strUpper.contains(tags[1])) {
            replayGain.setAlbumGain(parseReplayGainTag(str));
        }

        if (strUpper.contains(tags[2])) {
            replayGain.setTrackGain(parseReplayGainTag(str) / 256f + 5f);
        }

        if (strUpper.contains(tags[3])) {
            replayGain.setAlbumGain(parseReplayGainTag(str) / 256f + 5f);
        }

        return replayGain;
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

    private static void setReplayGain(Player player, float gain) {
        float preamp = Preferences.getReplayGainPreamp();
        float totalGain = gain + preamp;
        totalGain = Math.max(-60f, Math.min(15f, totalGain));
        player.setVolume((float) Math.pow(10f, totalGain / 20f));
    }
}
