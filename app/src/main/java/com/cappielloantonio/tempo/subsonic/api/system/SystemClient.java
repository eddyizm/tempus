package com.cappielloantonio.tempo.subsonic.api.system;

import android.util.Log;

import com.cappielloantonio.tempo.subsonic.RetrofitClient;
import com.cappielloantonio.tempo.subsonic.Subsonic;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.concurrent.TimeUnit;

import retrofit2.Call;

public class SystemClient {
    private static final String TAG = "SystemClient";

    private final Subsonic subsonic;
    private final SystemService systemService;

    public SystemClient(Subsonic subsonic) {
        this.subsonic = subsonic;
        this.systemService = new RetrofitClient(subsonic).getRetrofit().create(SystemService.class);
    }

    public Call<ApiResponse> ping() {
        Log.d(TAG, "ping()");
        Call<ApiResponse> pingCall = systemService.ping(subsonic.getParams());
        if (Preferences.isInUseServerAddressLocal()) {
            pingCall.timeout()
                    .timeout(1, TimeUnit.SECONDS);
        } else {
            pingCall.timeout()
                    .timeout(3, TimeUnit.SECONDS);
        }
        return pingCall;
    }

    public Call<ApiResponse> getLicense() {
        Log.d(TAG, "getLicense()");
        return systemService.getLicense(subsonic.getParams());
    }

    public Call<ApiResponse> getOpenSubsonicExtensions() {
        Log.d(TAG, "getOpenSubsonicExtensions()");
        return systemService.getOpenSubsonicExtensions(subsonic.getParams());
    }
}
