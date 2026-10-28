package com.eddyizm.tempus.util;

import androidx.annotation.Nullable;

import com.eddyizm.tempus.model.PendingPlaylistEdit;

import java.util.ArrayList;
import java.util.List;

/**
 * Folds one more phone edit into the playlist's pending edit. Every edit made before the next sync
 * shares the base of the first one, so the merge stays a comparison of three lists however many
 * times the playlist was touched. A failed edit stays failed until the user retries it.
 */
public final class PendingEditFold {
    private PendingEditFold() {
    }

    /**
     * @return the row to store, or null when there is no pending edit and no snapshot of the
     * server's order to start from, because a base guessed from the unordered cache could make the
     * merge drop tracks.
     */
    @Nullable
    public static PendingPlaylistEdit fold(@Nullable PendingPlaylistEdit existing, @Nullable List<String> snapshotIds, String playlistId, String server, List<String> localIds, @Nullable String newName, long now) {
        List<String> local = new ArrayList<>(localIds);

        if (existing == null) {
            if (snapshotIds == null) return null;
            return new PendingPlaylistEdit(playlistId, server, new ArrayList<>(snapshotIds), local, newName, now, PendingPlaylistEdit.PENDING, null);
        }

        String name = newName != null ? newName : existing.getNewName();
        return new PendingPlaylistEdit(playlistId, server, existing.getBaseIds(), local, name, now, existing.getState(), existing.getLastError());
    }
}
