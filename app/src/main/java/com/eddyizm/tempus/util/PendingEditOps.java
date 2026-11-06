package com.eddyizm.tempus.util;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The edits a pending playlist edit can take, applied to the phone's current list of track ids. */
public final class PendingEditOps {
    private PendingEditOps() {
    }

    public static List<String> append(List<String> current, List<String> added) {
        List<String> next = new ArrayList<>(current);
        next.addAll(added);
        return next;
    }

    /**
     * Removes expectedId at index, the position the page showed it at. The list underneath can have
     * moved since the page last loaded, so when that position holds another track the nearest copy
     * of expectedId goes instead, looking forward first since tracks merged in push rows down.
     * Null when the track is not in the list at all.
     */
    @Nullable
    public static List<String> removeAt(List<String> current, int index, String expectedId) {
        int at = -1;
        if (index >= 0 && index < current.size() && current.get(index).equals(expectedId)) {
            at = index;
        } else {
            for (int distance = 1; distance <= current.size() + Math.abs(index) && at < 0; distance++) {
                int after = index + distance;
                int before = index - distance;
                if (after >= 0 && after < current.size() && current.get(after).equals(expectedId)) at = after;
                else if (before >= 0 && before < current.size() && current.get(before).equals(expectedId)) at = before;
            }
        }
        if (at < 0) return null;

        List<String> next = new ArrayList<>(current);
        next.remove(at);
        return next;
    }
}
