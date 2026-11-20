package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FlushDecisionTest {

    private static List<String> ids(String... ids) {
        return Arrays.asList(ids);
    }

    @Test
    public void unchangedIsDrop() {
        FlushDecision decision = FlushDecision.decide(ids("a", "b"), ids("a", "b"), ids("a", "b"), null, "n");

        assertEquals(FlushDecision.Kind.DROP, decision.kind);
    }

    @Test
    public void retryAfterLostResponseIsNoop() {
        FlushDecision decision = FlushDecision.decide(ids("a", "b"), ids("a", "b", "c"), ids("a", "b", "c"), null, "n");

        assertEquals(FlushDecision.Kind.DROP, decision.kind);
    }

    @Test
    public void remoteChangedLocalChangedWrites() {
        FlushDecision decision = FlushDecision.decide(ids("a", "b", "c"), ids("c", "a"), ids("a", "b", "c", "x"), null, "n");

        assertEquals(FlushDecision.Kind.WRITE, decision.kind);
        assertEquals(ids("c", "x", "a"), decision.songs);
        assertNull(decision.name);
    }

    @Test
    public void renameOnlyWritesNameNotTracks() {
        FlushDecision decision = FlushDecision.decide(ids("a", "b"), ids("a", "b"), ids("a", "b"), "x", "y");

        assertEquals(FlushDecision.Kind.WRITE, decision.kind);
        assertNull(decision.songs);
        assertEquals("x", decision.name);
    }

    @Test
    public void sameNameIsNotRenamed() {
        FlushDecision decision = FlushDecision.decide(ids("a"), ids("a"), ids("a"), "y", "y");

        assertEquals(FlushDecision.Kind.DROP, decision.kind);
    }

    @Test
    public void emptyMergeIsRefused() {
        FlushDecision decision = FlushDecision.decide(ids("a", "b"), Collections.emptyList(), ids("a", "b"), null, "n");

        assertEquals(FlushDecision.Kind.REFUSE_EMPTY, decision.kind);
    }
}
