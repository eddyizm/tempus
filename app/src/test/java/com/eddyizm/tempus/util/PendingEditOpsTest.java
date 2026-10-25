package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PendingEditOpsTest {

    private static List<String> ids(String... ids) {
        return Arrays.asList(ids);
    }

    @Test
    public void removesTheTrackAtTheIndex() {
        assertEquals(ids("a", "c"), PendingEditOps.removeAt(ids("a", "b", "c"), 1, "b"));
    }

    @Test
    public void shiftedIndexRemovesTheExpectedTrack() {
        assertEquals(ids("x", "a", "c"), PendingEditOps.removeAt(ids("x", "a", "b", "c"), 1, "b"));
    }

    @Test
    public void shiftedIndexPicksTheNearestCopy() {
        assertEquals(ids("b", "a", "c", "b"), PendingEditOps.removeAt(ids("b", "a", "b", "c", "b"), 1, "b"));
    }

    @Test
    public void missingTrackIsRefused() {
        assertNull(PendingEditOps.removeAt(ids("a", "c"), 1, "b"));
    }

    @Test
    public void outOfRangeWithoutTheTrackIsRefused() {
        assertNull(PendingEditOps.removeAt(ids("a"), 5, "b"));
    }

    @Test
    public void addAppends() {
        assertEquals(ids("a", "b", "c"), PendingEditOps.append(ids("a"), ids("b", "c")));
    }
}
