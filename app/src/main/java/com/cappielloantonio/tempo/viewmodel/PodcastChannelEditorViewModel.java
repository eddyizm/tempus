package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.repository.PodcastRepository;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PodcastChannelEditorViewModel extends AndroidViewModel {
    private static final String TAG = "PodcastChannelEditorViewModel";

    private final PodcastRepository podcastRepository;

    private final MutableLiveData<Boolean> isSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public PodcastChannelEditorViewModel(@NonNull Application application) {
        super(application);
        podcastRepository = new PodcastRepository();
    }

    public LiveData<Boolean> getIsSuccess() {
        return isSuccess;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public void createChannel(String url) {
        errorMessage.setValue(null);
        isSuccess.setValue(false);

        podcastRepository.createPodcastChannel(url)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.code() == 501) {
                            showError(getApplication().getString(R.string.podcast_channel_not_supported_snackbar));
                            return;
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            ApiResponse apiResponse = response.body();

                            String status = apiResponse.subsonicResponse.getStatus();
                            if ("ok".equals(status)) {
                                isSuccess.setValue(true);
                            }
                        } else {
                            handleHttpError(response);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        showError("Network error: " + t.getMessage());
                        Log.e(TAG, "Network error", t);
                    }
                });
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
        showError(errorMsg);
    }

    private void showError(String message) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show();
        errorMessage.setValue(message);
        Log.e(TAG, "Error shown: " + message);
    }
}