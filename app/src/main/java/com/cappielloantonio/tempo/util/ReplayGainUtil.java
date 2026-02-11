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
import java.util.List;
import java.util.Objects;

@OptIn(markerClass = UnstableApi.class)
public class ReplayGainUtil {
    private static final String[] tags = {"REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN", "R128_TRACK_GAIN", "R128_ALBUM_GAIN"};

    public static void setReplayGain(Player player, Tracks tracks) {
        List<Metadata> metadata = getMetadata(tracks);
        List<ReplayGain> gains = getReplayGains(metadata);

        applyReplayGain(player, gains);
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
        for (String tag : tags) {
            if (entry.toString().contains(tag)) {
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

        if (str.contains(tags[0])) {
            replayGain.setTrackGain(parseReplayGainTag(str));
        }

        if (str.contains(tags[1])) {
            replayGain.setAlbumGain(parseReplayGainTag(str));
        }

        if (str.contains(tags[2])) {
            replayGain.setTrackGain(parseReplayGainTag(str) / 256f);
        }

        if (str.contains(tags[3])) {
            replayGain.setAlbumGain(parseReplayGainTag(str) / 256f);
        }

        return replayGain;
    }

    private static Float parseReplayGainTag(String entry) {
        try {
            return Float.parseFloat(entry.toString().replaceAll("[^\\d.-]", ""));
        } catch (NumberFormatException exception) {
            return 0f;
        }
    }

    private static void applyReplayGain(Player player, List<ReplayGain> gains) {
        if (Objects.equals(Preferences.getReplayGainMode(), "disabled") || gains == null || gains.isEmpty()) {
            setNoReplayGain(player);
            return;
        }

        if (Objects.equals(Preferences.getReplayGainMode(), "auto")) {
            if (areTracksConsecutive(player)) {
                setAutoReplayGain(player, gains);
            } else {
                setTrackReplayGain(player, gains);
            }

            return;
        }

        if (Objects.equals(Preferences.getReplayGainMode(), "track")) {
            setTrackReplayGain(player, gains);
            return;
        }

        if (Objects.equals(Preferences.getReplayGainMode(), "album")) {
            setAlbumReplayGain(player, gains);
            return;
        }

        setNoReplayGain(player);
    }

    private static void setNoReplayGain(Player player) {
        setReplayGain(player, 0f);
    }

    private static void setTrackReplayGain(Player player, List<ReplayGain> gains) {
        float trackGain = gains.get(0).getTrackGain() != 0f ? gains.get(0).getTrackGain() : gains.get(1).getTrackGain();

        setReplayGain(player, trackGain != 0f ? trackGain : 0f);
    }

    private static void setAlbumReplayGain(Player player, List<ReplayGain> gains) {
        float albumGain = gains.get(0).getAlbumGain() != 0f ? gains.get(0).getAlbumGain() : gains.get(1).getAlbumGain();

        setReplayGain(player, albumGain != 0f ? albumGain : 0f);
    }

    private static void setAutoReplayGain(Player player, List<ReplayGain> gains) {
        float albumGain = gains.get(0).getAlbumGain() != 0f ? gains.get(0).getAlbumGain() : gains.get(1).getAlbumGain();
        float trackGain = gains.get(0).getTrackGain() != 0f ? gains.get(0).getTrackGain() : gains.get(1).getTrackGain();

        setReplayGain(player, albumGain != 0f ? albumGain : trackGain);
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
        player.setVolume((float) Math.pow(10f, gain / 20f));
    }
}
