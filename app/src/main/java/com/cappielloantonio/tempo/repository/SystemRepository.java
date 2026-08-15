package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.github.models.LatestRelease;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.base.Resource;
import com.cappielloantonio.tempo.subsonic.base.ResourceCallback;
import com.cappielloantonio.tempo.subsonic.base.SubsonicCallback;
import com.cappielloantonio.tempo.subsonic.models.OpenSubsonicExtension;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SystemRepository {
    public void checkUserCredential(ResourceCallback<Void> callback) {
        callback.onResult(Resource.loading());

        App.getSubsonicClientInstance(false)
                .getSystemClient()
                .ping()
                .enqueue(new SubsonicCallback<Void>(callback) {
                    @Override
                    protected Void extractData(SubsonicResponse response) {
                        return null;
                    }
                });
    }

    public MutableLiveData<SubsonicResponse> ping() {
        MutableLiveData<SubsonicResponse> pingResult = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSystemClient()
                .ping()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            pingResult.postValue(response.body().getSubsonicResponse());
                        } else {
                            pingResult.postValue(null);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        pingResult.postValue(null);
                    }
                });

        return pingResult;
    }

    public MutableLiveData<List<OpenSubsonicExtension>> getOpenSubsonicExtensions() {
        MutableLiveData<List<OpenSubsonicExtension>> extensionsResult = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getSystemClient()
                .getOpenSubsonicExtensions()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            extensionsResult.postValue(response.body().getSubsonicResponse().getOpenSubsonicExtensions());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        extensionsResult.postValue(null);
                    }
                });

        return extensionsResult;
    }

    public MutableLiveData<LatestRelease> checkTempoUpdate() {
        MutableLiveData<LatestRelease> latestRelease = new MutableLiveData<>();

        App.getGithubClientInstance()
                .getReleaseClient()
                .getLatestRelease()
                .enqueue(new Callback<LatestRelease>() {
                    @Override
                    public void onResponse(@NonNull Call<LatestRelease> call, @NonNull Response<LatestRelease> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            latestRelease.postValue(response.body());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LatestRelease> call, @NonNull Throwable t) {
                        latestRelease.postValue(null);
                    }
                });

        return latestRelease;
    }
}
