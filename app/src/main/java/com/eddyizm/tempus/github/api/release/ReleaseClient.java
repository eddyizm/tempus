package com.eddyizm.tempus.github.api.release;

import android.util.Log;

import com.eddyizm.tempus.github.Github;
import com.eddyizm.tempus.github.GithubRetrofitClient;
import com.eddyizm.tempus.github.models.LatestRelease;

import retrofit2.Call;

public class ReleaseClient {
    private static final String TAG = "ReleaseClient";

    private final ReleaseService releaseService;

    public ReleaseClient(Github github) {
        this.releaseService = new GithubRetrofitClient(github).getRetrofit().create(ReleaseService.class);
    }

    public Call<LatestRelease> getLatestRelease() {
        Log.d(TAG, "getLatestRelease()");
        return releaseService.getLatestRelease(Github.getOwner(), Github.getRepo());
    }
}
