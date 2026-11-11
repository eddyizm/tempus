package com.eddyizm.tempus.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Combines a playlist edited on the phone while the server was out of reach with whatever the
 * server holds now. base is the list the phone's edits started from, local is the phone's list,
 * remote is the server's list read just before writing.
 * <p>
 * The phone's order wins for every track both sides kept. A track the server dropped since base
 * stays dropped, and a track the server gained since base is kept, placed after the nearest track
 * it followed on the server. A playlist can hold the same track more than once, so a track is
 * told apart by its id and by how many times that id came before it in the same list.
 */
public final class PlaylistMerge {
    private PlaylistMerge() {
    }

    public static List<String> merge(List<String> base, List<String> local, List<String> remote) {
        if (remote.equals(base)) return new ArrayList<>(local);
        if (local.equals(base)) return new ArrayList<>(remote);

        List<Entry> baseEntries = tag(base);
        List<Entry> remoteEntries = tag(remote);
        Set<Entry> inBase = new HashSet<>(baseEntries);
        Set<Entry> inRemote = new HashSet<>(remoteEntries);

        List<Entry> result = new ArrayList<>();
        for (Entry entry : tag(local)) {
            if (inBase.contains(entry) && !inRemote.contains(entry)) continue;
            result.add(entry);
        }

        Set<Entry> inResult = new HashSet<>(result);
        for (int i = 0; i < remoteEntries.size(); i++) {
            Entry added = remoteEntries.get(i);
            if (inBase.contains(added) || inResult.contains(added)) continue;

            int at = 0;
            for (int j = i - 1; j >= 0; j--) {
                Entry before = remoteEntries.get(j);
                if (inResult.contains(before)) {
                    at = result.indexOf(before) + 1;
                    break;
                }
            }
            result.add(at, added);
            inResult.add(added);
        }

        List<String> ids = new ArrayList<>(result.size());
        for (Entry entry : result) ids.add(entry.id);
        return ids;
    }

    private static List<Entry> tag(List<String> ids) {
        Map<String, Integer> seen = new HashMap<>();
        List<Entry> entries = new ArrayList<>(ids.size());
        for (String id : ids) {
            int occurrence = seen.getOrDefault(id, 0);
            seen.put(id, occurrence + 1);
            entries.add(new Entry(id, occurrence));
        }
        return entries;
    }

    private static final class Entry {
        final String id;
        final int occurrence;

        Entry(String id, int occurrence) {
            this.id = id;
            this.occurrence = occurrence;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry)) return false;
            Entry other = (Entry) o;
            return occurrence == other.occurrence && Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, occurrence);
        }
    }
}
