package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.repository.RadioRepository;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RadioEditorViewModel extends AndroidViewModel {
    private static final String TAG = "RadioEditorViewModel";

    private final RadioRepository radioRepository;
    private InternetRadioStation toEdit;
    
    private final MutableLiveData<Boolean> isSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public RadioEditorViewModel(@NonNull Application application) {
        super(application);
        radioRepository = new RadioRepository();
    }


    public LiveData<Boolean> getIsSuccess() { return isSuccess; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    
    public void clearError() {
        errorMessage.setValue(null);
    }

    public InternetRadioStation getRadioToEdit() {
        return toEdit;
    }

    public void setRadioToEdit(InternetRadioStation internetRadioStation) {
        this.toEdit = internetRadioStation;
    }

    public void createRadio(String name, String streamURL, String homepageURL) {
        errorMessage.setValue(null);
        isSuccess.setValue(false);
        
        radioRepository.createInternetRadioStation(name, streamURL, homepageURL)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        // Handle HTTP 501 (Not Implemented) from Navidrome
                        if (response.code() == 501) {
                            showError(getApplication().getString(R.string.radio_dialog_not_supported_snackbar));
                            return;
                        }
                        if (response.isSuccessful() && response.body() != null) {
                            ApiResponse apiResponse = response.body();
                            String status = apiResponse.subsonicResponse.getStatus();
                            if ("ok".equals(status)) {
                                isSuccess.setValue(true);
                            } else if ("failed".equals(status)) {
                                handleFailedResponse(apiResponse);
                            }
                        } else {
                            errorMessage.setValue("HTTP error: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        errorMessage.setValue("Network error: " + t.getMessage());
                    }
                });
    }

    public void updateRadio(String name, String streamURL, String homepageURL) {
        if (toEdit != null && toEdit.getId() != null) {
            errorMessage.setValue(null);
            isSuccess.setValue(false);
            
            radioRepository.updateInternetRadioStation(toEdit.getId(), name, streamURL, homepageURL)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                ApiResponse apiResponse = response.body();
                                if (apiResponse.subsonicResponse != null) {
                                    String status = apiResponse.subsonicResponse.getStatus();
                                    if ("ok".equals(status)) {
                                        isSuccess.setValue(true);
                                    } else if ("failed".equals(status)) {
                                        handleFailedResponse(apiResponse);
                                    }
                                }
                            } else {
                                errorMessage.setValue("HTTP error: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            errorMessage.setValue("Network error: " + t.getMessage());
                        }
                    });
        }
    }

    public void deleteRadio() {
        if (toEdit != null && toEdit.getId() != null) {
            errorMessage.setValue(null);
            isSuccess.setValue(false);
            
            radioRepository.deleteInternetRadioStation(toEdit.getId())
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                ApiResponse apiResponse = response.body();

                                String status = apiResponse.subsonicResponse.getStatus();

                                if ("ok".equals(status)) {
                                    isSuccess.setValue(true);
                                } else if ("failed".equals(status)) {
                                    handleFailedResponse(apiResponse);
                                }
                            } else {
                                errorMessage.setValue("HTTP error: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            errorMessage.setValue("Network error: " + t.getMessage());
                        }
                    });
        }
    }

    private void showError(String message) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show();
        errorMessage.setValue(message);
    }

    private void handleFailedResponse(ApiResponse apiResponse) {
        String errorMsg = "Unknown server error";
        
        if (apiResponse.subsonicResponse.getError() != null) {
            errorMsg = apiResponse.subsonicResponse.getError().getMessage();
            
            if ("Not implemented".equals(errorMsg)) {
                errorMsg = getApplication().getString((R.string.radio_dialog_not_supported_snackbar));
            }
        }
        
        Toast.makeText(getApplication(), errorMsg, Toast.LENGTH_LONG).show();
        
        errorMessage.setValue(errorMsg);
    }
}