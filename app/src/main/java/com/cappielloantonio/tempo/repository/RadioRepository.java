package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.model.InternetRadioStationCache;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.util.RadioCoverArtDownloader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
                            cacheSubsonicStations(stations);
                            mergeWithLocal(radioStation, stations);
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

    private void cacheSubsonicStations(List<InternetRadioStation> stations) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance();
            db.internetRadioStationDao().deleteSubsonic();
            List<InternetRadioStationCache> cacheList = stations.stream()
                    .map(InternetRadioStationCache::new)
                    .collect(Collectors.toList());
            db.internetRadioStationDao().insertAll(cacheList);

            for (InternetRadioStationCache cache : cacheList) {
                if (cache.getCoverArtUrl() != null && !cache.getCoverArtUrl().isEmpty()) {
                    RadioCoverArtDownloader.downloadCoverArt(cache.getId(), cache.getCoverArtUrl());
                }
            }
        }).start();
    }

    private void mergeWithLocal(MutableLiveData<List<InternetRadioStation>> liveData, List<InternetRadioStation> subsonicStations) {
        new Thread(() -> {
            List<InternetRadioStationCache> localCaches = AppDatabase.getInstance().internetRadioStationDao().getLocal();
            List<InternetRadioStation> localStations = localCaches.stream()
                    .map(InternetRadioStationCache::toInternetRadioStation)
                    .collect(Collectors.toList());

            List<InternetRadioStation> merged = new ArrayList<>(subsonicStations);
            merged.addAll(localStations);
            sortByName(merged);
            liveData.postValue(merged);
        }).start();
    }

    private void fallbackToCache(MutableLiveData<List<InternetRadioStation>> liveData) {
        new Thread(() -> {
            List<InternetRadioStation> cached = AppDatabase.getInstance().internetRadioStationDao().getAll().stream()
                    .map(InternetRadioStationCache::toInternetRadioStation)
                    .collect(Collectors.toList());
            if (!cached.isEmpty()) {
                sortByName(cached);
                liveData.postValue(cached);
            }
        }).start();
    }

    private void sortByName(List<InternetRadioStation> stations) {
        stations.sort(Comparator.comparing(
                station -> station.getName() == null ? "" : station.getName(),
                String.CASE_INSENSITIVE_ORDER));
    }

    public void createLocalStation(String name, String streamUrl, String homepageUrl, String coverArtUrl, Runnable onComplete) {
        new Thread(() -> {
            String id = "local_" + UUID.randomUUID().toString();
            InternetRadioStationCache cache = new InternetRadioStationCache();
            cache.setId(id);
            cache.setName(name);
            cache.setStreamUrl(streamUrl);
            cache.setHomePageUrl(homepageUrl);
            cache.setSource(InternetRadioStationCache.SOURCE_LOCAL);
            cache.setCoverArtUrl(coverArtUrl);

            AppDatabase.getInstance().internetRadioStationDao().insert(cache);

            if (coverArtUrl != null && !coverArtUrl.isEmpty()) {
                RadioCoverArtDownloader.downloadCoverArt(id, coverArtUrl);
            }

            if (onComplete != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
            }
        }).start();
    }

    public void updateLocalStation(String id, String name, String streamUrl, String homepageUrl, String coverArtUrl, Runnable onComplete) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance();
            InternetRadioStationCache cache = db.internetRadioStationDao().getById(id);
            if (cache != null) {
                cache.setName(name);
                cache.setStreamUrl(streamUrl);
                cache.setHomePageUrl(homepageUrl);
                cache.setCoverArtUrl(coverArtUrl);
                db.internetRadioStationDao().update(cache);

                if (coverArtUrl != null && !coverArtUrl.isEmpty()) {
                    RadioCoverArtDownloader.downloadCoverArt(id, coverArtUrl);
                }
            }

            if (onComplete != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
            }
        }).start();
    }

    public void deleteLocalStation(String id, Runnable onComplete) {
        new Thread(() -> {
            AppDatabase.getInstance().internetRadioStationDao().deleteById(id);
            RadioCoverArtDownloader.deleteCoverArt(id);

            if (onComplete != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(onComplete);
            }
        }).start();
    }

    public boolean isLocalStation(String stationId) {
        return stationId != null && stationId.startsWith("local_");
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
