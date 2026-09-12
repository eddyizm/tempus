package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.repository.PodcastRepository;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.PodcastChannel;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PodcastChannelPageViewModel extends AndroidViewModel {
    private static final String TAG = "PodcastChannelPageViewModel";
    private final PodcastRepository podcastRepository;

    private PodcastChannel podcastChannel;

    public PodcastChannelPageViewModel(@NonNull Application application) {
        super(application);

        podcastRepository = new PodcastRepository();
    }

    public LiveData<List<PodcastChannel>> getPodcastChannelEpisodes() {
        return podcastRepository.getPodcastChannels(true, podcastChannel.getId());
    }

    public PodcastChannel getPodcastChannel() {
        return podcastChannel;
    }

    public void setPodcastChannel(PodcastChannel podcastChannel) {
        this.podcastChannel = podcastChannel;
    }

    public void requestPodcastEpisodeDownload(PodcastEpisode podcastEpisode) {
        if (podcastEpisode != null && podcastEpisode.getId() != null) {
            podcastRepository.downloadPodcastEpisode(podcastEpisode.getId())
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && "ok".equals(response.body().getSubsonicResponse().getStatus())) {
                                Log.d(TAG, "downloadPodcastEpisode successful for id: " + podcastEpisode.getId());
                            } else {
                                Log.e(TAG, "downloadPodcastEpisode failed with code: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            Log.e(TAG, "downloadPodcastEpisode network error", t);
                        }
                    });
        }
    }
}
