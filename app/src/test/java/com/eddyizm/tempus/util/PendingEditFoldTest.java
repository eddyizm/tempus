package com.eddyizm.tempus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.eddyizm.tempus.model.PendingPlaylistEdit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PendingEditFoldTest {

    private static List<String> ids(String... ids) {
        return Arrays.asList(ids);
    }

    private static PendingPlaylistEdit row(String state, String name) {
        return new PendingPlaylistEdit("p", "s", ids("a", "b"), ids("b"), name, 1L, state, "e");
    }

    @Test
    public void newRowUsesSnapshotAsBase() {
        PendingPlaylistEdit edit = PendingEditFold.fold(null, ids("a", "b"), "p", "s", ids("b", "a"), null, 5L);

        assertEquals(ids("a", "b"), edit.getBaseIds());
        assertEquals(ids("b", "a"), edit.getLocalIds());
        assertEquals(PendingPlaylistEdit.PENDING, edit.getState());
        assertNull(edit.getNewName());
        assertNull(edit.getLastError());
        assertEquals(5L, edit.getUpdatedAt());
        assertEquals("p", edit.getPlaylistId());
        assertEquals("s", edit.getServerId());
    }

    @Test
    public void queueWithoutBaseIsRefused() {
        assertNull(PendingEditFold.fold(null, null, "p", "s", ids("a"), null, 5L));
    }

    @Test
    public void secondEditKeepsOriginalBase() {
        PendingPlaylistEdit edit = PendingEditFold.fold(row(PendingPlaylistEdit.PENDING, null), ids("x"), "p", "s", ids("c"), null, 9L);

        assertEquals(ids("a", "b"), edit.getBaseIds());
        assertEquals(ids("c"), edit.getLocalIds());
        assertEquals(9L, edit.getUpdatedAt());
    }

    @Test
    public void secondEditKeepsFailedState() {
        PendingPlaylistEdit edit = PendingEditFold.fold(row(PendingPlaylistEdit.FAILED, null), null, "p", "s", ids("c"), null, 9L);

        assertEquals(PendingPlaylistEdit.FAILED, edit.getState());
        assertEquals("e", edit.getLastError());
    }

    @Test
    public void nameKeptWhenNotPassed() {
        PendingPlaylistEdit edit = PendingEditFold.fold(row(PendingPlaylistEdit.PENDING, "old"), null, "p", "s", ids("c"), null, 9L);

        assertEquals("old", edit.getNewName());
    }

    @Test
    public void nameReplacedWhenPassed() {
        PendingPlaylistEdit edit = PendingEditFold.fold(row(PendingPlaylistEdit.PENDING, "old"), null, "p", "s", ids("c"), "new", 9L);

        assertEquals("new", edit.getNewName());
    }
}
