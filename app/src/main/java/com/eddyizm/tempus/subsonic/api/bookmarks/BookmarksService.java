package com.eddyizm.tempus.subsonic.api.bookmarks;

import com.eddyizm.tempus.subsonic.base.ApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface BookmarksService {
    @GET("getPlayQueue")
    Call<ApiResponse> getPlayQueue(@QueryMap Map<String, String> params);

    @GET("savePlayQueue")
    Call<ApiResponse> savePlayQueue(@QueryMap Map<String, String> params, @Query("id") List<String> ids, @Query("current") String current, @Query("position") long position);
}
