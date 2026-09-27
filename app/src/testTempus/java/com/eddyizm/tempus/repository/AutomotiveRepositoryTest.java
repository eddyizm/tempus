package com.eddyizm.tempus.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.media.browse.MediaBrowser;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaConstants;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.subsonic.models.AlbumWithSongsID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.DiscTitle;
import com.eddyizm.tempus.subsonic.models.InternetRadioStation;
import com.eddyizm.tempus.util.ConstantsAA;
import com.eddyizm.tempus.util.MappingUtil;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AutomotiveRepositoryTest {

    private static InternetRadioStation station(String name) {
        return new InternetRadioStation(null, name, null, null, null, null);
    }

    private static List<String> names(List<InternetRadioStation> stations) {
        return stations.stream().map(InternetRadioStation::getName).collect(Collectors.toList());
    }

    private static MediaItem mediaItemWithDisc(int disc) {
        return new MediaItem.Builder()
                .setMediaId("track-" + disc)
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setDiscNumber(disc)
                                .build())
                .build();
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
    public void multiDiscAlbumGroupsTracksByDiscNumber() {
        List<MediaItem> mediaItems = Arrays.asList(
                mediaItemWithDisc(1),
                mediaItemWithDisc(2)
        );

        Context context = mock(Context.class);

        when(context.getString(R.string.disc_titleless, "1")).thenReturn("Disc 1");
        when(context.getString(R.string.disc_titleless, "2")).thenReturn("Disc 2");

        var result = AutomotiveRepository.getMultiDiscTitles(context, new ArrayList<>(), mediaItems);

        assertEquals("Disc 1", result.get(1));
        assertEquals("Disc 2", result.get(2));
    }

    @Test
    public void singleDiscAlbumDoesNotGroupTracks() {
        List<MediaItem> mediaItems = Arrays.asList(
                mediaItemWithDisc(1),
                mediaItemWithDisc(1)
        );

        var result = AutomotiveRepository.getMultiDiscTitles(
                mock(Context.class),
                new ArrayList<>(),
                mediaItems);

        assertTrue(result.isEmpty());
    }

    @Test
    public void multiDiscAlbumUsesDiscTitles() {
        List<MediaItem> mediaItems = Arrays.asList(
                mediaItemWithDisc(1),
                mediaItemWithDisc(2)
        );

        List<DiscTitle> discTitles = Arrays.asList(
                new DiscTitle(1, "Dawn to Dusk"),
                new DiscTitle(2, "Twilight to Starlight")
        );

        Context context = mock(Context.class);

        when(context.getString(R.string.disc_titlefull, "1", "Dawn to Dusk"))
                .thenReturn("Disc 1 - Dawn to Dusk");
        when(context.getString(R.string.disc_titlefull, "2", "Twilight to Starlight"))
                .thenReturn("Disc 2 - Twilight to Starlight");

        var result = AutomotiveRepository.getMultiDiscTitles(context, discTitles, mediaItems);

        assertEquals("Disc 1 - Dawn to Dusk", result.get(1));
        assertEquals("Disc 2 - Twilight to Starlight", result.get(2));
    }
}
