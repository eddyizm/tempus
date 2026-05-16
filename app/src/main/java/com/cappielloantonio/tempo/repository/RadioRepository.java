package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.model.InternetRadioStationCache;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioRepository {
    public MutableLiveData<List<InternetRadioStation>> getInternetRadioStations() {
        MutableLiveData<List<InternetRadioStation>> radioStation = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .getInternetRadioStations()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getInternetRadioStations() != null && response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations() != null) {
                            List<InternetRadioStation> stations = response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations();
                            radioStation.setValue(stations);
                            cacheStations(stations);
                        } else {
                            fallbackToCache(radioStation);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        fallbackToCache(radioStation);
                    }
                });

        return radioStation;
    }

    private void cacheStations(List<InternetRadioStation> stations) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance();
            db.internetRadioStationDao().deleteAll();
            List<InternetRadioStationCache> cacheList = stations.stream()
                    .map(InternetRadioStationCache::new)
                    .collect(Collectors.toList());
            db.internetRadioStationDao().insertAll(cacheList);
        }).start();
    }

    private void fallbackToCache(MutableLiveData<List<InternetRadioStation>> liveData) {
        new Thread(() -> {
            List<InternetRadioStation> cached = AppDatabase.getInstance().internetRadioStationDao().getAll().stream()
                    .map(InternetRadioStationCache::toInternetRadioStation)
                    .collect(Collectors.toList());
            if (!cached.isEmpty()) {
                liveData.postValue(cached);
            }
        }).start();
    }

        public Call<ApiResponse> createInternetRadioStation(String name, String streamURL, String homepageURL) {
        return App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .createInternetRadioStation(streamURL, name, homepageURL);
    }

    public Call<ApiResponse> updateInternetRadioStation(String id, String name, String streamURL, String homepageURL) {
        return App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .updateInternetRadioStation(id, streamURL, name, homepageURL);
    }

    public Call<ApiResponse> deleteInternetRadioStation(String id) {
        return App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .deleteInternetRadioStation(id);
    }

}
