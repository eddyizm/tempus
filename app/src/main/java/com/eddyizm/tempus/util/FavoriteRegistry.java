package com.eddyizm.tempus.util;

import java.util.HashMap;
import java.util.Map;

public final class FavoriteRegistry {
    public enum Kind {SONG, ALBUM, ARTIST}

    // One request's decision and the one before it. A refusal strikes its own record and, skipping
    // records already struck, the unaccepted records beneath it that carry the same decision, since
    // those are earlier attempts at the same decision. Any other failed request is withdrawn alone
    // unless the retry queue takes over its decision, and a decision handed to that queue strikes the
    // pending records above the newest accepted one that say the opposite. Resolve reads past struck
    // records. A record the server accepted is never struck, and accepting one lifts a strike it
    // took while its request was still open.
    public static final class Record {
        private final String key;
        private final boolean starred;
        private final Record previous;
        private boolean accepted;
        private boolean struck;

        private Record(String key, boolean starred, Record previous) {
            this.key = key;
            this.starred = starred;
            this.previous = previous;
        }
    }

    private static final Map<String, Record> records = new HashMap<>();

    private FavoriteRegistry() {
    }

    public static synchronized Record set(Kind kind, String id, boolean isStarred) {
        if (id == null) return null;
        String key = key(kind, id);
        Record record = new Record(key, isStarred, records.get(key));
        records.put(key, record);
        return record;
    }

    public static synchronized boolean resolve(Kind kind, String id, boolean serverSaysStarred) {
        Record live = id != null ? surviving(records.get(key(kind, id))) : null;
        return live != null ? live.starred : serverSaysStarred;
    }

    public static synchronized boolean isCurrent(Record record) {
        return record != null && surviving(records.get(record.key)) == record;
    }

    public static synchronized void accept(Record record) {
        if (record == null) return;
        record.accepted = true;
        record.struck = false;
    }

    public static synchronized void strike(Record record) {
        for (Record r = record; r != null; r = r.previous) {
            if (r.struck) continue;
            if (r.accepted || r.starred != record.starred) break;
            r.struck = true;
        }
    }

    public static synchronized void withdraw(Record record) {
        if (record != null && !record.accepted) record.struck = true;
    }

    public static synchronized void supersede(Kind kind, String id, boolean isStarred) {
        if (id == null) return;
        for (Record r = records.get(key(kind, id)); r != null && !r.accepted; r = r.previous) {
            if (r.starred != isStarred) r.struck = true;
        }
    }

    public static synchronized void clear() {
        records.clear();
    }

    private static Record surviving(Record record) {
        while (record != null && record.struck) record = record.previous;
        return record;
    }

    private static String key(Kind kind, String id) {
        return kind.name() + ":" + id;
    }
}
