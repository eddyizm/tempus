package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlaylistMergeTest {

    private static List<String> ids(String... ids) {
        return new ArrayList<>(Arrays.asList(ids));
    }

    @Test
    public void remoteUnchangedTakesLocal() {
        assertEquals(ids("c", "a"), PlaylistMerge.merge(ids("a", "b", "c"), ids("c", "a"), ids("a", "b", "c")));
    }

    @Test
    public void localUnchangedTakesRemote() {
        assertEquals(ids("a", "c", "d"), PlaylistMerge.merge(ids("a", "b", "c"), ids("a", "b", "c"), ids("a", "c", "d")));
    }

    @Test
    public void remoteAddIsKeptAfterItsNeighbour() {
        assertEquals(ids("c", "b", "x", "a"), PlaylistMerge.merge(ids("a", "b", "c"), ids("c", "b", "a"), ids("a", "b", "x", "c")));
    }

    @Test
    public void remoteAddAtStart() {
        assertEquals(ids("x", "b", "a"), PlaylistMerge.merge(ids("a", "b"), ids("b", "a"), ids("x", "a", "b")));
    }

    @Test
    public void remoteRemovalWins() {
        assertEquals(ids("c", "a"), PlaylistMerge.merge(ids("a", "b", "c"), ids("c", "b", "a"), ids("a", "c")));
    }

    @Test
    public void bothRemoveSame() {
        assertEquals(ids("a", "c"), PlaylistMerge.merge(ids("a", "b", "c"), ids("a", "c"), ids("a", "c")));
    }

    @Test
    public void bothAddDifferent() {
        assertEquals(ids("a", "b", "x", "y"), PlaylistMerge.merge(ids("a", "b"), ids("a", "b", "y"), ids("a", "b", "x")));
    }

    @Test
    public void duplicatesKeepOccurrences() {
        assertEquals(ids("a", "a", "x", "b"), PlaylistMerge.merge(ids("a", "b", "a"), ids("a", "a", "b"), ids("a", "b", "a", "x")));
    }

    @Test
    public void sameAddOnBothSidesNotDuplicated() {
        assertEquals(ids("a", "x"), PlaylistMerge.merge(ids("a"), ids("a", "x"), ids("a", "x")));
    }

    @Test
    public void localReaddOfRemotelyRemovedSurvives() {
        assertEquals(ids("a", "b"), PlaylistMerge.merge(ids("a", "b"), ids("a", "b", "b"), ids("a")));
    }

    @Test
    public void emptyBase() {
        assertEquals(ids("b", "a"), PlaylistMerge.merge(Collections.emptyList(), ids("a"), ids("b")));
    }

    @Test
    public void inputsNotMutated() {
        List<String> base = ids("a", "b", "c");
        List<String> local = ids("c", "b", "a");
        List<String> remote = ids("a", "b", "x", "c");

        PlaylistMerge.merge(base, local, remote);

        assertEquals(ids("a", "b", "c"), base);
        assertEquals(ids("c", "b", "a"), local);
        assertEquals(ids("a", "b", "x", "c"), remote);
    }
}
