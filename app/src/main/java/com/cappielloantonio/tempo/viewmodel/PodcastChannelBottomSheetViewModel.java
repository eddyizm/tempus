package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.cappielloantonio.tempo.repository.PodcastRepository;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.PodcastChannel;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PodcastChannelBottomSheetViewModel extends AndroidViewModel {
    private static final String TAG = "PodcastChannelBottomSheetViewModel";
    private final PodcastRepository podcastRepository;

    private PodcastChannel podcastChannel;

    public PodcastChannelBottomSheetViewModel(@NonNull Application application) {
        super(application);

        podcastRepository = new PodcastRepository();
    }

    public PodcastChannel getPodcastChannel() {
        return podcastChannel;
    }

    public void setPodcastChannel(PodcastChannel podcastChannel) {
        this.podcastChannel = podcastChannel;
    }

    public void deletePodcastChannel() {
        if (podcastChannel != null && podcastChannel.getId() != null) {
            podcastRepository.deletePodcastChannel(podcastChannel.getId())
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.code() == 501) {
                                Toast.makeText(getApplication(), 
                                    "Podcasts are not supported by this server", 
                                    Toast.LENGTH_LONG).show();
                                return;
                            }

                            if (response.isSuccessful() && response.body() != null) {
                                ApiResponse apiResponse = response.body();

                                String status = apiResponse.subsonicResponse.getStatus();

                                if ("ok".equals(status)) {
                                    Toast.makeText(getApplication(),
                                        "Podcast channel deleted",
                                        Toast.LENGTH_SHORT).show();
                                    //TODO refresh the UI after deleting
                                    //podcastRepository.refreshPodcasts();
                                }
                            } else {
                                handleHttpError(response);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            Toast.makeText(getApplication(), 
                                "Network error: " + t.getMessage(), 
                                Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void handleHttpError(Response<ApiResponse> response) {
        String errorMsg = "HTTP error: " + response.code();
        if (response.errorBody() != null) {
            try {
                String serverMsg = response.errorBody().string();
                if (!serverMsg.isEmpty()) {
                    errorMsg += " - " + serverMsg;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading error body", e);
            }
        }
        
        Toast.makeText(getApplication(), errorMsg, Toast.LENGTH_LONG).show();
    }

}
