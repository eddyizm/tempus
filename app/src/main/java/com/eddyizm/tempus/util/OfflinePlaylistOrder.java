package com.eddyizm.tempus.util;

import com.eddyizm.tempus.model.PlaylistSong;
import com.eddyizm.tempus.subsonic.models.Child;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a playlist's tracks from the local cache in a known order. The playlist_song table keeps
 * one row per track id and no position, so the order, and any track listed twice, come from a
 * separate list of ids, the server's last known order or the phone's pending edit.
 */
public final class OfflinePlaylistOrder {
    private OfflinePlaylistOrder() {
    }

    public static List<Child> order(List<String> ids, Map<String, PlaylistSong> cachedById) {
        List<Child> songs = new ArrayList<>(ids.size());
        for (String id : ids) {
            PlaylistSong cached = cachedById.get(id);
            songs.add(cached != null ? toChild(cached) : new Child(id));
        }
        return songs;
    }

    public static Child toChild(PlaylistSong cached) {
        Child child = new Child(cached.getId());
        child.setTitle(cached.getTitle());
        child.setArtist(cached.getArtist());
        child.setAlbum(cached.getAlbum());
        child.setTrack(cached.getTrack());
        child.setCoverArtId(cached.getCoverArtId());
        child.setDuration(cached.getDuration());
        child.setAlbumId(cached.getAlbumId());
        child.setArtistId(cached.getArtistId());
        return child;
    }
}
