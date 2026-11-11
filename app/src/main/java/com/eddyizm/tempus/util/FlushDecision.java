package com.eddyizm.tempus.util;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * What a sync does with one pending playlist edit, given the server's list read just before.
 * The decision compares the merged list with the server's, not with the phone's, so a retry after a
 * write that landed but whose answer was lost finds nothing left to send.
 */
public final class FlushDecision {
    public enum Kind { DROP, WRITE, REFUSE_EMPTY }

    public final Kind kind;
    /** The full list to write, or null when the tracks already match. */
    @Nullable
    public final List<String> songs;
    /** The name to write, or null when the name already matches. */
    @Nullable
    public final String name;

    private FlushDecision(Kind kind, @Nullable List<String> songs, @Nullable String name) {
        this.kind = kind;
        this.songs = songs;
        this.name = name;
    }

    public static FlushDecision decide(List<String> base, List<String> local, List<String> remote, @Nullable String newName, @Nullable String remoteName) {
        List<String> merged = PlaylistMerge.merge(base, local, remote);
        if (merged.isEmpty() && !remote.isEmpty()) return new FlushDecision(Kind.REFUSE_EMPTY, null, null);

        List<String> songs = merged.equals(remote) ? null : merged;
        String name = newName != null && !newName.equals(remoteName) ? newName : null;
        if (songs == null && name == null) return new FlushDecision(Kind.DROP, null, null);

        return new FlushDecision(Kind.WRITE, songs, name);
    }
}
