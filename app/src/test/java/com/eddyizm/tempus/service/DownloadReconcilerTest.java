package com.eddyizm.tempus.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.offline.Download;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DownloadReconcilerTest {

    private final Map<String, Integer> index = new HashMap<>();
    private final Set<String> requested = new HashSet<>();

    private DownloadReconciler.Plan plan(String... pendingIds) {
        return DownloadReconciler.plan(Arrays.asList(pendingIds), index::get, requested::contains);
    }

    private static List<String> ids(String... ids) {
        return Arrays.asList(ids);
    }

    @Test
    public void completedInTheIndexIsMarkedCompleted() {
        index.put("1", Download.STATE_COMPLETED);

        DownloadReconciler.Plan plan = plan("1");

        assertEquals(ids("1"), plan.markCompleted);
        assertTrue(plan.deleteRows.isEmpty());
        assertTrue(plan.removeFromIndex.isEmpty());
    }

    @Test
    public void failedInTheIndexIsDroppedFromBoth() {
        index.put("1", Download.STATE_FAILED);

        DownloadReconciler.Plan plan = plan("1");

        assertEquals(ids("1"), plan.removeFromIndex);
        assertEquals(ids("1"), plan.deleteRows);
        assertTrue(plan.markCompleted.isEmpty());
    }

    @Test
    public void unknownToTheIndexIsDeleted() {
        DownloadReconciler.Plan plan = plan("1");

        assertEquals(ids("1"), plan.deleteRows);
        assertTrue(plan.removeFromIndex.isEmpty());
    }

    @Test
    public void stillRunningInMedia3IsLeftAlone() {
        index.put("queued", Download.STATE_QUEUED);
        index.put("downloading", Download.STATE_DOWNLOADING);
        index.put("restarting", Download.STATE_RESTARTING);
        index.put("stopped", Download.STATE_STOPPED);
        index.put("removing", Download.STATE_REMOVING);

        assertTrue(plan("queued", "downloading", "restarting", "stopped", "removing").isEmpty());
    }

    @Test
    public void requestedIsLeftAloneWhateverTheIndexSays() {
        // Not indexed yet, a failed entry being retried, a completed one asked for again.
        index.put("failed", Download.STATE_FAILED);
        index.put("completed", Download.STATE_COMPLETED);
        requested.addAll(ids("new", "failed", "completed"));

        assertTrue(plan("new", "failed", "completed").isEmpty());
    }

    @Test
    public void mixedRowsAreSortedOutInOrder() {
        index.put("a", Download.STATE_COMPLETED);
        index.put("b", Download.STATE_FAILED);
        index.put("d", Download.STATE_COMPLETED);
        index.put("e", Download.STATE_DOWNLOADING);

        DownloadReconciler.Plan plan = plan("a", "b", "c", "d", "e");

        assertEquals(ids("a", "d"), plan.markCompleted);
        assertEquals(ids("b"), plan.removeFromIndex);
        assertEquals(ids("b", "c"), plan.deleteRows);
    }

    @Test
    public void nothingPendingGivesAnEmptyPlan() {
        assertTrue(DownloadReconciler.plan(Collections.emptyList(), index::get, requested::contains).isEmpty());
    }
}
