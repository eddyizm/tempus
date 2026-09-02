package com.eddyizm.tempus.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.eddyizm.tempus.subsonic.models.InternetRadioStation;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.subsonic.models.AlbumWithSongsID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.DiscTitle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AutomotiveRepositoryTest {

    private static InternetRadioStation station(String name) {
        return new InternetRadioStation(null, name, null, null, null, null);
    }

    private static Child track(String id, String title, int trackNumber, int discNumber) {
        Child track = new Child(id);
        track.setTitle(title);
        track.setTrack(trackNumber);
        track.setDiscNumber(discNumber);
        return track;
    }

    private static List<String> names(List<InternetRadioStation> stations) {
        return stations.stream().map(InternetRadioStation::getName).collect(Collectors.toList());
    }

    @Test
    public void localRadiosShownWhenServerHasNone() {
        // #810: a server with zero radio stations must not hide the user's locally-added radios.
        List<InternetRadioStation> result = AutomotiveRepository.mergeAndSortRadioStations(
                new ArrayList<>(),
                Arrays.asList(station("Local FM"), station("Another Local")));

        assertEquals(Arrays.asList("Another Local", "Local FM"), names(result));
    }

    @Test
    public void serverAndLocalRadiosAreMergedAndSortedCaseInsensitively() {
        List<InternetRadioStation> result = AutomotiveRepository.mergeAndSortRadioStations(
                Arrays.asList(station("zeta"), station("alpha")),
                Arrays.asList(station("Beta")));

        assertEquals(Arrays.asList("alpha", "Beta", "zeta"), names(result));
    }

    @Test
    public void nullStationNamesSortFirstWithoutCrashing() {
        List<InternetRadioStation> result = AutomotiveRepository.mergeAndSortRadioStations(
                Arrays.asList(station("b")),
                Arrays.asList(station(null), station("a")));

        assertEquals(Arrays.asList(null, "a", "b"), names(result));
    }

    @Test
    public void trackNumbersShownWhenSettingEnabled() {
        Child track = track("1", "Track One", 1, 1);

        assertEquals("1. Track One", AutomotiveRepository.getTrackDisplayTitle(track, true));
    }

    @Test
    public void trackNumbersNotShownWhenSettingDisabled() {
        Child track = track("1", "Track One", 1, 1);

        assertNull(AutomotiveRepository.getTrackDisplayTitle(track, false));
    }

    @Test
    public void singleDiscAlbumHasNoDiscHeader() {
        Context context = mock(Context.class);
        AlbumWithSongsID3 album = new AlbumWithSongsID3();

        List<Child> tracks = Arrays.asList(
                track("1", "Track One", 1, 1),
                track("2", "Track Two", 2, 1));

        assertNull(AutomotiveRepository.getDiscHeaderTitle(context, album, tracks.getFirst()));
    }

    @Test
    public void multiDiscAlbumWithoutTitlesShowsDiscNumber() {
        Context context = mock(Context.class);
        AlbumWithSongsID3 album = new AlbumWithSongsID3();

        List<Child> tracks = Arrays.asList(
                track("1", "First Disc, Last Track", 14, 1),
                track("2", "Second Disc, First Track", 1, 2));

        when(context.getString(R.string.disc_titleless, "2")).thenReturn("Disc 2");

        assertEquals("Disc 2", AutomotiveRepository.getDiscHeaderTitle(context, album, tracks.get(1)));
    }

    @Test
    public void multiDiscAlbumWithTitlesShowsDiscNumberAndTitle() {
        Context context = mock(Context.class);
        AlbumWithSongsID3 album = new AlbumWithSongsID3();

        album.setDiscTitles(Arrays.asList(
                new DiscTitle(1, "Title of Disc 1"),
                new DiscTitle(2, "Title of Disc 2")));

        List<Child> tracks = Arrays.asList(
                track("1", "First Disc, Last Track", 14, 1),
                track("2", "Second Disc, First Track", 1, 2));

        when(context.getString(R.string.disc_titlefull, "2", "Title of Disc 2")).thenReturn("Disc 2 - Title of Disc 2");

        assertEquals("Disc 2 - Title of Disc 2", AutomotiveRepository.getDiscHeaderTitle(context, album, tracks.get(1)));
    }
}
