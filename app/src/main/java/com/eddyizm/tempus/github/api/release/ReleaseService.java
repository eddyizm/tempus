package com.eddyizm.tempus.github.api.release;

import com.eddyizm.tempus.github.models.LatestRelease;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ReleaseService {
    @GET("repos/{owner}/{repo}/releases/latest")
    Call<LatestRelease> getLatestRelease(@Path("owner") String owner, @Path("repo") String repo);
}
