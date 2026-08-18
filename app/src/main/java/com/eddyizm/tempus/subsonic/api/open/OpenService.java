package com.eddyizm.tempus.subsonic.api.open;

import com.eddyizm.tempus.subsonic.base.ApiResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface OpenService {
    @GET("getLyricsBySongId")
    Call<ApiResponse> getLyricsBySongId(@QueryMap Map<String, String> params, @Query("id") String id);
}
