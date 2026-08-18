package com.cappielloantonio.tempo.radiobrowser;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface RadioBrowserService {
    @GET("json/stations/search")
    Call<List<RadioBrowserStation>> searchStations(@QueryMap Map<String, String> params);

    @GET("json/stations/search")
    Call<List<RadioBrowserStation>> searchByName(@Query("name") String name, @Query("limit") int limit, @Query("order") String order, @Query("reverse") boolean reverse);

    @GET("json/stations/search")
    Call<List<RadioBrowserStation>> searchByCountryExact(@Query("countryexact") String country, @Query("limit") int limit, @Query("order") String order, @Query("reverse") boolean reverse);

    @GET("json/countries")
    Call<List<RadioBrowserCountry>> getCountries();
}
