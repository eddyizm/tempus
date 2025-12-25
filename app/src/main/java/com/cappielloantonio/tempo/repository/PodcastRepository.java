package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.PodcastChannel;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PodcastRepository {
    private static final String TAG = "PodcastRepository";

    public MutableLiveData<List<PodcastChannel>> getPodcastChannels(boolean includeEpisodes, String channelId) {
        MutableLiveData<List<PodcastChannel>> livePodcastChannel = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .getPodcasts(includeEpisodes, channelId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPodcasts() != null) {
                            livePodcastChannel.setValue(response.body().getSubsonicResponse().getPodcasts().getChannels());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return livePodcastChannel;
    }

    public MutableLiveData<List<PodcastEpisode>> getNewestPodcastEpisodes(int count) {
        MutableLiveData<List<PodcastEpisode>> liveNewestPodcastEpisodes = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .getNewestPodcasts(count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getNewestPodcasts() != null) {
                            liveNewestPodcastEpisodes.setValue(response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return liveNewestPodcastEpisodes;
    }

    public Call<ApiResponse> refreshPodcasts() {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .refreshPodcasts();
    }

    public Call<ApiResponse> createPodcastChannel(String url) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .createPodcastChannel(url);
    }

    public Call<ApiResponse> deletePodcastChannel(String channelId) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .deletePodcastChannel(channelId);
    }

    public Call<ApiResponse> deletePodcastEpisode(String episodeId) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .deletePodcastEpisode(episodeId);
    }

    public Call<ApiResponse> downloadPodcastEpisode(String episodeId) {
        return App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .downloadPodcastEpisode(episodeId);
    }
}
