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

        assertSortedAndLabelled(Constants.DOWNLOAD_TYPE_TRACK, songs, "Alpha", "A");
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
                child("blank", "  ", null, null, null, null),
                child("named", "Named", null, null, null, null),
                child("missing", null, null, null, null, null)
        );

        adapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, Constants.DOWNLOAD_TYPE_TRACK, null, songs);

        assertEquals("Named", adapter.getItem(0).getTitle());
        assertEquals("  ", adapter.getItem(1).getTitle());
        assertEquals("N", adapter.getTextToShowInBubble(0));
        assertNull(adapter.getTextToShowInBubble(1));
        assertNull(adapter.getTextToShowInBubble(2));
        assertNull(adapter.getTextToShowInBubble(-1));
        assertNull(adapter.getTextToShowInBubble(3));
    }

    @Test
    public void filteredRowsRemainSortedByTheirDisplayedLabel() {
        List<Child> songs = Arrays.asList(
                child("z", "Zebra", "Shared Album", "Artist", "Rock", 2000),
                child("a", "Alpha", "Shared Album", "Artist", "Rock", 2000)
        );

        adapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, Constants.DOWNLOAD_TYPE_ALBUM, "Shared Album", songs);

        assertEquals(2, adapter.getItemCount());
        assertEquals("Alpha", adapter.getItem(0).getTitle());
        assertEquals("A", adapter.getTextToShowInBubble(0));
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
        Child child = new Child(id);
        child.setTitle(title);
        child.setAlbum(album);
        child.setAlbumId(album);
        child.setArtist(artist);
        child.setArtistId(artist);
        child.setGenre(genre);
        child.setYear(year);
        return child;
    }

    private Download playlist(String id, String name) {
        Download download = new Download(id, id, name, 1, null);
        download.setTitle(id);
        return download;
    }
}
