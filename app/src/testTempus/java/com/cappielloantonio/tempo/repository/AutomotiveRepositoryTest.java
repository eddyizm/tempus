package com.cappielloantonio.tempo.repository;

import static org.junit.Assert.assertEquals;

import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;

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
}
