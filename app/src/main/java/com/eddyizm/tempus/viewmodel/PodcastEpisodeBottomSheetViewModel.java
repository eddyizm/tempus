package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.eddyizm.tempus.repository.PodcastRepository;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PodcastEpisodeBottomSheetViewModel extends AndroidViewModel {
    private static final String TAG = "PodcastEpisodeBottomSheetViewModel";

    private final PodcastRepository podcastRepository;

    private PodcastEpisode podcastEpisode;

    public PodcastEpisodeBottomSheetViewModel(@NonNull Application application) {
        super(application);

        podcastRepository = new PodcastRepository();
    }

    public PodcastEpisode getPodcastEpisode() {
        return podcastEpisode;
    }

    public void setPodcastEpisode(PodcastEpisode podcast) {
        this.podcastEpisode = podcast;
    }

    public void deletePodcastEpisode() {
        if (podcastEpisode != null && podcastEpisode.getId() != null) {
            podcastRepository.deletePodcastEpisode(podcastEpisode.getId())
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null && "ok".equals(response.body().getSubsonicResponse().getStatus())) {
                                Toast.makeText(getApplication(), "Podcast episode deleted", Toast.LENGTH_SHORT).show();
                            } else {
                                Log.e(TAG, "deletePodcastEpisode failed with code: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            Log.e(TAG, "deletePodcastEpisode network error", t);
                        }
                    });
        }
    }
}
