package com.eddyizm.tempus.service;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Settles download rows left at download_state = 0 against the Media3 download index.
 *
 * A row is written at 0 when a download is asked for and only reaches 1 through the
 * DownloadManager listener. When that never happens, the row stays at 0 for good and the
 * Downloads screen, which lists state 1 only, hides the song even though its file is on disk:
 * the process died between Media3 recording the completion and the asynchronous Room update,
 * or the download failed before #1200 and Media3 kept the failed entry, which made the old
 * startup cleanup skip the row.
 *
 * Kept free of Android and Room so the decision can be unit tested.
 */
@UnstableApi
public final class DownloadReconciler {

    private DownloadReconciler() {
    }

    public static final class Plan {
        /** Media3 holds the whole file: the row should read as downloaded. */
        public final List<String> markCompleted;
        /** Media3 gave up on these: drop the index entry so it cannot pin the row again. */
        public final List<String> removeFromIndex;
        /** Rows nothing will ever finish. */
        public final List<String> deleteRows;

        Plan(List<String> markCompleted, List<String> removeFromIndex, List<String> deleteRows) {
            this.markCompleted = Collections.unmodifiableList(markCompleted);
            this.removeFromIndex = Collections.unmodifiableList(removeFromIndex);
            this.deleteRows = Collections.unmodifiableList(deleteRows);
        }

        public boolean isEmpty() {
            return markCompleted.isEmpty() && removeFromIndex.isEmpty() && deleteRows.isEmpty();
        }
    }

    /**
     * @param pendingIds  ids of the rows at download_state = 0
     * @param indexState  the Media3 state of an id, or null when the index does not know it
     * @param isRequested whether the id was handed to the service in this process and has not finished yet
     */
    public static Plan plan(List<String> pendingIds,
                            Function<String, Integer> indexState,
                            Predicate<String> isRequested) {
        List<String> markCompleted = new ArrayList<>();
        List<String> removeFromIndex = new ArrayList<>();
        List<String> deleteRows = new ArrayList<>();

        for (String id : pendingIds) {
            // In flight in this process: the listener settles it, and the state seen here may
            // predate the request (a failed entry about to be retried, or not indexed yet).
            if (isRequested.test(id)) continue;

            @Nullable Integer state = indexState.apply(id);

            if (state == null) {
                deleteRows.add(id);
            } else if (state == Download.STATE_COMPLETED) {
                markCompleted.add(id);
            } else if (state == Download.STATE_FAILED) {
                removeFromIndex.add(id);
                deleteRows.add(id);
            }
            // Queued, downloading, restarting, stopped or removing: still Media3's to finish,
            // and the listener settles the row when it does.
        }

        return new Plan(markCompleted, removeFromIndex, deleteRows);
    }
}
