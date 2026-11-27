package com.eddyizm.tempus.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.eddyizm.tempus.subsonic.models.AlbumID3;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class ArtistPageViewModelTest {

    private static AlbumID3 album(int songCount, List<String> releaseTypes) {
        AlbumID3 album = new AlbumID3();
        album.setSongCount(songCount);
        album.setReleaseTypes(releaseTypes);
        return album;
    }

    @Test
    public void sectionType_usesTheReportedReleaseType() {
        assertEquals("ep", ArtistPageViewModel.sectionType(album(10, Arrays.asList("EP"))));
        assertEquals("live", ArtistPageViewModel.sectionType(album(1, Arrays.asList("Live", "Album"))));
    }

    // gonic reports [""] rather than omitting the field. Without the blank check the group
    // key is "" and AlbumSectionsAdapter reads the first character of it.
    @Test
    public void sectionType_fallsBackToTheSongCountWhenTheReleaseTypeIsBlank() {
        assertEquals("album", ArtistPageViewModel.sectionType(album(10, Arrays.asList(""))));
        assertEquals("single", ArtistPageViewModel.sectionType(album(2, Arrays.asList("   "))));
    }

    @Test
    public void sectionType_fallsBackToTheSongCountWithNoReleaseTypeAtAll() {
        assertEquals("album", ArtistPageViewModel.sectionType(album(8, null)));
        assertEquals("ep", ArtistPageViewModel.sectionType(album(3, Collections.emptyList())));
        assertEquals("single", ArtistPageViewModel.sectionType(album(2, Collections.singletonList(null))));
    }

    @Test
    public void groupAlbums_putsEverythingInOneShelfWhenCategorizationIsOff() {
        List<AlbumID3> albums = Arrays.asList(
                album(10, Collections.singletonList("EP")),
                album(2, Collections.singletonList("Single")),
                album(12, Collections.singletonList("Album")));

        Map<String, List<AlbumID3>> grouped = ArtistPageViewModel.groupAlbums(albums, false);

        assertEquals(Collections.singleton("album"), grouped.keySet());
        assertSame(albums, grouped.get("album"));
    }

    @Test
    public void groupAlbums_keepsTheExistingSectionsWhenCategorizationIsOn() {
        List<AlbumID3> albums = Arrays.asList(
                album(10, Collections.singletonList("EP")),
                album(2, Collections.singletonList("Single")));

        Map<String, List<AlbumID3>> grouped = ArtistPageViewModel.groupAlbums(albums, true);

        assertEquals(2, grouped.size());
        assertEquals(1, grouped.get("ep").size());
        assertEquals(1, grouped.get("single").size());
    }

    @Test
    public void groupAlbums_returnsNoShelvesWhenThereAreNoAlbums() {
        assertTrue(ArtistPageViewModel.groupAlbums(Collections.emptyList(), false).isEmpty());
        assertTrue(ArtistPageViewModel.groupAlbums(null, true).isEmpty());
    }
}
