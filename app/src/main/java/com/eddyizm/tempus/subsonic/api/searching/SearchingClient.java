package com.eddyizm.tempus.subsonic.api.searching;

import android.util.Log;

import com.eddyizm.tempus.subsonic.RetrofitClient;
import com.eddyizm.tempus.subsonic.Subsonic;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.util.Preferences;

import retrofit2.Call;

public class SearchingClient {
    private static final String TAG = "BrowsingClient";

    private final Subsonic subsonic;
    private final SearchingService searchingService;

    public SearchingClient(Subsonic subsonic) {
        this.subsonic = subsonic;
        this.searchingService = new RetrofitClient(subsonic).getRetrofit().create(SearchingService.class);
    }

    public Call<ApiResponse> search2(String query, int songCount, int albumCount, int artistCount) {
        Log.d(TAG, "search2()");
        return searchingService.search2(subsonic.getParams(), query, songCount, albumCount, artistCount);
    }

    public Call<ApiResponse> search3(String query, int songCount, int songOffset, int albumCount, int albumOffset, int artistCount, int artistOffset) {
        Log.d(TAG, "search3()");
        return searchingService.search3(subsonic.getParams(), query, songCount, songOffset, albumCount, albumOffset, artistCount, artistOffset, Preferences.getActiveMusicFolderId());
    }
}
