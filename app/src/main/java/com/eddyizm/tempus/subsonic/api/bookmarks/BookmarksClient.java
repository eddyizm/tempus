package com.eddyizm.tempus.subsonic.api.bookmarks;

import android.util.Log;

import com.eddyizm.tempus.subsonic.RetrofitClient;
import com.eddyizm.tempus.subsonic.Subsonic;
import com.eddyizm.tempus.subsonic.base.ApiResponse;

import java.util.List;

import retrofit2.Call;

public class BookmarksClient {
    private static final String TAG = "BookmarksClient";

    private final Subsonic subsonic;
    private final BookmarksService bookmarksService;

    public BookmarksClient(Subsonic subsonic) {
        this.subsonic = subsonic;
        this.bookmarksService = new RetrofitClient(subsonic).getRetrofit().create(BookmarksService.class);
    }

    public Call<ApiResponse> getPlayQueue() {
        Log.d(TAG, "getPlayQueue()");
        return bookmarksService.getPlayQueue(subsonic.getParams());
    }

    public Call<ApiResponse> savePlayQueue(List<String> ids, String current, long position) {
        Log.d(TAG, "savePlayQueue()");
        return bookmarksService.savePlayQueue(subsonic.getParams(), ids, current, position);
    }

    public Call<ApiResponse> getBookmarks() {
        Log.d(TAG, "getBookmarks()");
        return bookmarksService.getBookmarks(subsonic.getParams());
    }

    public Call<ApiResponse> createBookmark(String id, long positionMs) {
        Log.d(TAG, "createBookmark() id=" + id + " position=" + positionMs);
        return bookmarksService.createBookmark(subsonic.getParams(), id, positionMs);
    }

    public Call<ApiResponse> deleteBookmark(String id) {
        Log.d(TAG, "deleteBookmark() id=" + id);
        return bookmarksService.deleteBookmark(subsonic.getParams(), id);
    }
}
