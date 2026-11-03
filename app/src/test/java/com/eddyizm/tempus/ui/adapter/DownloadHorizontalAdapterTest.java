package com.eddyizm.tempus.ui.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.util.Constants;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DownloadHorizontalAdapterTest {
    private DownloadHorizontalAdapter adapter;

    @Before
    public void setUp() {
        adapter = spy(new DownloadHorizontalAdapter(mock(ClickCallback.class)));
        doNothing().when(adapter).notifyDataSetChanged();
    }

    @Test
    public void sortsRowsAndProvidesBubbleLabelsForEachGrouping() {
        List<Child> songs = Arrays.asList(
                child("z", "Zebra", "Zulu Album", "Zeta Artist", "Rock", 2002),
                child("a", "Alpha", "Amber Album", "Able Artist", "Ambient", 1999)
        );

        // Track view preserves original song order (track/disc number order), with bubble matching album artist or album
        adapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, Constants.DOWNLOAD_TYPE_TRACK, null, new ArrayList<>(songs));
        assertEquals("Zebra", adapter.getItem(0).getTitle());
        assertEquals("Z", adapter.getTextToShowInBubble(0));

        assertSortedAndLabelled(Constants.DOWNLOAD_TYPE_ALBUM, songs, "Amber Album", "A");
        assertSortedAndLabelled(Constants.DOWNLOAD_TYPE_ARTIST, songs, "Able Artist", "A");
        assertSortedAndLabelled(Constants.DOWNLOAD_TYPE_GENRE, songs, "Ambient", "A");
        assertSortedAndLabelled(Constants.DOWNLOAD_TYPE_YEAR, songs, "1999", "1");

        List<Child> playlists = Arrays.asList(
                playlist("z", "Zulu Playlist"),
                playlist("a", "Alpha Playlist")
        );
        assertSortedAndLabelled(Constants.DOWNLOAD_TYPE_PLAYLIST, playlists, "Alpha Playlist", "A");
    }

    @Test
    public void nullAndBlankLabelsSortLastAndHaveNoBubbleText() {
        List<Child> songs = Arrays.asList(
                child("blank", "Track 1", "Album", "  ", "Genre", 2000),
                child("named", "Track 2", "Album", "Named", "Genre", 2000),
                child("missing", "Track 3", "Album", null, "Genre", 2000)
        );

        adapter.setItems(Constants.DOWNLOAD_TYPE_ARTIST, Constants.DOWNLOAD_TYPE_ARTIST, null, songs);

        assertEquals("Named", adapter.getItem(0).getArtist());
        assertEquals("  ", adapter.getItem(1).getArtist());
        assertEquals("N", adapter.getTextToShowInBubble(0));
        assertNull(adapter.getTextToShowInBubble(1));
        assertNull(adapter.getTextToShowInBubble(2));
        assertNull(adapter.getTextToShowInBubble(-1));
        assertNull(adapter.getTextToShowInBubble(3));
    }

    @Test
    public void tracksWithinAlbumRetainOriginalTrackOrder() {
        // Tracks within an album should stay in track/disc order from songs, NOT sorted alphabetically by title
        List<Child> songs = Arrays.asList(
                child("z", "Zebra", "Shared Album", "Artist", "Rock", 2000),
                child("a", "Alpha", "Shared Album", "Artist", "Rock", 2000)
        );

        adapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, Constants.DOWNLOAD_TYPE_ALBUM, "Shared Album", songs);

        assertEquals(2, adapter.getItemCount());
        assertEquals("Zebra", adapter.getItem(0).getTitle());
        assertEquals("Alpha", adapter.getItem(1).getTitle());
        // Bubble reflects album artist or album title for tracks
        assertEquals("S", adapter.getTextToShowInBubble(0));
    }

    @Test
    public void albumSortingPrioritizesAlbumArtistAndIsCaseInsensitive() {
        List<Child> albums = Arrays.asList(
                child("3", "Track 3", "Charlie Album", "Artist C", "Rock", 2020, null),
                child("1", "Track 1", "Zeta Album", "Artist Z", "Rock", 2020, "Alpha Artist"),
                child("2", "Track 2", "beta album", "Artist B", "Rock", 2020, "bravo artist")
        );

        adapter.setItems(Constants.DOWNLOAD_TYPE_ALBUM, Constants.DOWNLOAD_TYPE_ALBUM, null, albums);

        assertEquals(3, adapter.getItemCount());
        // 1st: "Zeta Album" by "Alpha Artist"
        assertEquals("Zeta Album", adapter.getItem(0).getAlbum());
        assertEquals("A", adapter.getTextToShowInBubble(0));

        // 2nd: "beta album" by "bravo artist" (lowercase does not drop to the end)
        assertEquals("beta album", adapter.getItem(1).getAlbum());
        assertEquals("B", adapter.getTextToShowInBubble(1));

        // 3rd: "Charlie Album" (no album artist, falls back to album title)
        assertEquals("Charlie Album", adapter.getItem(2).getAlbum());
        assertEquals("C", adapter.getTextToShowInBubble(2));
    }

    private void assertSortedAndLabelled(String type, List<Child> items, String expectedLabel, String expectedBubble) {
        adapter.setItems(type, type, null, new ArrayList<>(items));

        assertEquals(expectedLabel, getLabel(type, adapter.getItem(0)));
        assertEquals(expectedBubble, adapter.getTextToShowInBubble(0));
    }

    private String getLabel(String type, Child item) {
        switch (type) {
            case Constants.DOWNLOAD_TYPE_TRACK:
                return item.getTitle();
            case Constants.DOWNLOAD_TYPE_ALBUM:
                return item.getAlbum();
            case Constants.DOWNLOAD_TYPE_ARTIST:
                return item.getArtist();
            case Constants.DOWNLOAD_TYPE_GENRE:
                return item.getGenre();
            case Constants.DOWNLOAD_TYPE_YEAR:
                return item.getYear().toString();
            case Constants.DOWNLOAD_TYPE_PLAYLIST:
                return ((Download) item).getPlaylistName();
            default:
                throw new IllegalArgumentException(type);
        }
    }

    private Child child(String id, String title, String album, String artist, String genre, Integer year) {
        return child(id, title, album, artist, genre, year, null);
    }

    private Child child(String id, String title, String album, String artist, String genre, Integer year, String albumArtist) {
        Child child = new Child(id);
        child.setTitle(title);
        child.setAlbum(album);
        child.setAlbumId(album);
        child.setArtist(artist);
        child.setArtistId(artist);
        child.setGenre(genre);
        child.setYear(year);
        child.setAlbumArtist(albumArtist);
        return child;
    }

    private Download playlist(String id, String name) {
        Download download = new Download(id, id, name, 1, null);
        download.setTitle(id);
        return download;
    }
}
