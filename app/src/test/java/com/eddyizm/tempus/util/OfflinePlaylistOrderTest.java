package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.eddyizm.tempus.model.PlaylistSong;
import com.eddyizm.tempus.subsonic.models.Child;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OfflinePlaylistOrderTest {

    private static PlaylistSong song(String id) {
        return new PlaylistSong("p", id, "title " + id, "artist " + id, "album " + id, 3, "cover " + id, 200, "album-id " + id, "artist-id " + id);
    }

    private static Map<String, PlaylistSong> cached(String... ids) {
        Map<String, PlaylistSong> map = new HashMap<>();
        for (String id : ids) map.put(id, song(id));
        return map;
    }

    private static String idsOf(List<Child> children) {
        StringBuilder out = new StringBuilder();
        for (Child child : children) out.append(child.getId());
        return out.toString();
    }

    @Test
    public void followsIdOrder() {
        assertEquals("ba", idsOf(OfflinePlaylistOrder.order(Arrays.asList("b", "a"), cached("a", "b"))));
    }

    @Test
    public void offlineOrderKeepsDuplicates() {
        assertEquals("aba", idsOf(OfflinePlaylistOrder.order(Arrays.asList("a", "b", "a"), cached("a", "b"))));
    }

    @Test
    public void uncachedIdGetsBareChild() {
        List<Child> children = OfflinePlaylistOrder.order(Arrays.asList("z"), cached("a"));

        assertEquals("z", children.get(0).getId());
        assertNull(children.get(0).getTitle());
    }

    @Test
    public void copiesMetadata() {
        Child child = OfflinePlaylistOrder.order(Arrays.asList("a"), cached("a")).get(0);

        assertEquals("title a", child.getTitle());
        assertEquals("artist a", child.getArtist());
        assertEquals("album a", child.getAlbum());
        assertEquals(Integer.valueOf(3), child.getTrack());
        assertEquals("cover a", child.getCoverArtId());
        assertEquals(Integer.valueOf(200), child.getDuration());
        assertEquals("album-id a", child.getAlbumId());
        assertEquals("artist-id a", child.getArtistId());
    }
}
