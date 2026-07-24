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

/**
 * Stores export intent (mediaId -> target export URI) for downloads that
 * should be copied to external storage upon completion by Media3.
 */
public final class ExternalDownloadMetadataStore {

    private static final String PREF_KEY = "external_download_metadata_v2";
    
    private static final Set<String> purgingDownloads = Collections.synchronizedSet(new HashSet<>());

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

    /**
     * Records the target directory URI for a specific media item.
     */
    public static synchronized void recordExportTarget(String mediaId, String directoryUri) {
        if (mediaId == null || directoryUri == null) {
            return;
        }
        JSONObject object = readAll();
        try {
            object.put(mediaId, directoryUri);
        } catch (JSONException ignored) {
        }
        writeAll(object);
    }

    public static synchronized void remove(String mediaId) {
        if (mediaId == null) {
            return;
        }
        JSONObject object = readAll();
        object.remove(mediaId);
        writeAll(object);
    }

    @Nullable
    public static synchronized String getExportTarget(String mediaId) {
        if (mediaId == null) {
            return null;
        }
        JSONObject object = readAll();
        if (!object.has(mediaId)) {
            return null;
        }
        return object.optString(mediaId, null);
    }

    public static void addPurging(String mediaId) {
        if (mediaId != null) purgingDownloads.add(mediaId);
    }

    public static void removePurging(String mediaId) {
        if (mediaId != null) purgingDownloads.remove(mediaId);
    }

    public static boolean isPurging(String mediaId) {
        return mediaId != null && purgingDownloads.contains(mediaId);
    }
}