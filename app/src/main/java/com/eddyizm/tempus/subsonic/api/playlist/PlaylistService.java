package com.eddyizm.tempus.subsonic.api.playlist;

import com.eddyizm.tempus.subsonic.base.ApiResponse;

import java.util.ArrayList;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface PlaylistService {
    @GET("getPlaylists")
    Call<ApiResponse> getPlaylists(@QueryMap Map<String, String> params);

    @GET("getPlaylist")
    Call<ApiResponse> getPlaylist(@QueryMap Map<String, String> params, @Query("id") String id);

    @GET("createPlaylist")
    Call<ApiResponse> createPlaylist(@QueryMap Map<String, String> params, @Query("playlistId") String playlistId, @Query("name") String name, @Query("songId") ArrayList<String> songsId);

    /**
     * isPublic is boxed so a caller can decline to set a visibility at all. Retrofit leaves a query
     * parameter out of the request when its value is null, and a primitive can never be null, which
     * is why public used to go out on every call.
     */
    @GET("updatePlaylist")
    Call<ApiResponse> updatePlaylist(@QueryMap Map<String, String> params, @Query("playlistId") String playlistId, @Query("name") String name, @Query("public") Boolean isPublic, @Query("songIdToAdd") ArrayList<String> songIdToAdd, @Query("songIndexToRemove") ArrayList<Integer> songIndexToRemove);

    /*
     * The same calls for the sync of edits kept on the phone. The HTTP cache keeps answers for a
     * minute, and the sync has to read the server's list as it is now and know its writes arrived.
     */
    @Headers("Cache-Control: no-cache")
    @GET("getPlaylist")
    Call<ApiResponse> getPlaylistFresh(@QueryMap Map<String, String> params, @Query("id") String id);

    @Headers("Cache-Control: no-cache")
    @GET("createPlaylist")
    Call<ApiResponse> createPlaylistFresh(@QueryMap Map<String, String> params, @Query("playlistId") String playlistId, @Query("name") String name, @Query("songId") ArrayList<String> songsId);

    @Headers("Cache-Control: no-cache")
    @GET("updatePlaylist")
    Call<ApiResponse> updatePlaylistFresh(@QueryMap Map<String, String> params, @Query("playlistId") String playlistId, @Query("name") String name, @Query("public") Boolean isPublic, @Query("songIdToAdd") ArrayList<String> songIdToAdd, @Query("songIndexToRemove") ArrayList<Integer> songIndexToRemove);

    @GET("deletePlaylist")
    Call<ApiResponse> deletePlaylist(@QueryMap Map<String, String> params, @Query("id") String id);
}
