package com.cappielloantonio.tempo.subsonic.api.jukeboxcontrol;

import com.cappielloantonio.tempo.subsonic.base.ApiResponse;

import java.util.Map;
import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface JukeboxControlService {
    @GET("jukeboxControl")
    Call<ApiResponse> jukeboxControl(@QueryMap Map<String, String> params, @Query("action") String action, @Query("index") Integer index, @Query("offset") Integer offset, @Query("id") List<String> ids, @Query("gain") Float gain );
}
