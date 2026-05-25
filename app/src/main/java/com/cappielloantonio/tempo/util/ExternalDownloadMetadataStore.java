package com.cappielloantonio.tempo.util;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.cappielloantonio.tempo.App;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class ExternalDownloadMetadataStore {

    private static final String PREF_KEY = "external_download_metadata";

    private ExternalDownloadMetadataStore() {
    }

    private static SharedPreferences preferences() {
        return App.getInstance().getPreferences();
    }

    private static JSONObject readAll() {
        String raw = preferences().getString(PREF_KEY, "{}");
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void writeAll(JSONObject object) {
        preferences().edit().putString(PREF_KEY, object.toString()).apply();
    }

    public static synchronized void clear() {
        writeAll(new JSONObject());
    }

    public static synchronized void recordSize(String key, long size) {
        if (key == null || size <= 0) {
            return;
        }
        JSONObject object = readAll();
        try {
            object.put(key, size);
        } catch (JSONException ignored) {
        }
        writeAll(object);
    }

    public static synchronized void remove(String key) {
        if (key == null) {
            return;
        }
        JSONObject object = readAll();
        object.remove(key);
        writeAll(object);
    }

    @Nullable
    public static synchronized Long getSize(String key) {
        if (key == null) {
            return null;
        }
        JSONObject object = readAll();
        if (!object.has(key)) {
            return null;
        }
        long size = object.optLong(key, -1L);
        return size > 0 ? size : null;
    }

    public static synchronized Map<String, Long> snapshot() {
        JSONObject object = readAll();
        if (object.length() == 0) {
            return Collections.emptyMap();
        }
        Map<String, Long> sizes = new HashMap<>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            long size = object.optLong(key, -1L);
            if (size > 0) {
                sizes.put(key, size);
            }
        }
        return sizes;
    }

    public static synchronized void retainOnly(Set<String> keysToKeep) {
        if (keysToKeep == null || keysToKeep.isEmpty()) {
            clear();
            return;
        }
        JSONObject object = readAll();
        if (object.length() == 0) {
            return;
        }
        Set<String> keys = new HashSet<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }
        boolean changed = false;
        for (String key : keys) {
            if (!keysToKeep.contains(key)) {
                object.remove(key);
                changed = true;
            }
        }
        if (changed) {
            writeAll(object);
        }
    }
}