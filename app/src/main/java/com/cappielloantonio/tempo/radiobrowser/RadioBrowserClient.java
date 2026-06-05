package com.cappielloantonio.tempo.radiobrowser;

import android.util.Log;

import com.cappielloantonio.tempo.BuildConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RadioBrowserClient {
    private static final String TAG = "RadioBrowserClient";
    private static final int SEARCH_LIMIT = 30;

    // radio-browser.info is served by a pool of mirrors; using a single host is a SPOF, so we
    // try the others in turn on network failure. Each mirror has a valid cert for its own host,
    // so rewriting the request host is TLS-safe.
    private static final List<String> MIRRORS = Arrays.asList(
            "de1.api.radio-browser.info",
            "de2.api.radio-browser.info",
            "nl1.api.radio-browser.info",
            "at1.api.radio-browser.info"
    );
    private static final String USER_AGENT = "Tempo/" + BuildConfig.VERSION_NAME;

    private static RadioBrowserClient instance;
    private final RadioBrowserService service;

    private RadioBrowserClient() {
        List<String> mirrors = new ArrayList<>(MIRRORS);
        Collections.shuffle(mirrors);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()))
                .addInterceptor(new MirrorFailoverInterceptor(mirrors))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://" + mirrors.get(0) + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        service = retrofit.create(RadioBrowserService.class);
    }

    public static synchronized RadioBrowserClient getInstance() {
        if (instance == null) {
            instance = new RadioBrowserClient();
        }
        return instance;
    }

    public Call<List<RadioBrowserStation>> searchByName(String name) {
        Log.d(TAG, "searchByName: " + name);
        return service.searchByName(name, SEARCH_LIMIT, "votes", true);
    }

    public Call<List<RadioBrowserStation>> searchByCountryExact(String country) {
        Log.d(TAG, "searchByCountryExact: " + country);
        return service.searchByCountryExact(country, SEARCH_LIMIT, "votes", true);
    }

    public Call<List<RadioBrowserStation>> searchAdvanced(String name, String country, String language, String tag) {
        Log.d(TAG, "searchAdvanced: name=" + name + " country=" + country);
        HashMap<String, String> params = new HashMap<>();
        if (name != null && !name.isEmpty()) params.put("name", name);
        if (country != null && !country.isEmpty()) params.put("country", country);
        if (language != null && !language.isEmpty()) params.put("language", language);
        if (tag != null && !tag.isEmpty()) params.put("tag", tag);
        params.put("limit", String.valueOf(SEARCH_LIMIT));
        params.put("order", "votes");
        params.put("reverse", "true");
        return service.searchStations(params);
    }

    public Call<List<RadioBrowserCountry>> getCountries() {
        Log.d(TAG, "getCountries");
        return service.getCountries();
    }

    // Retries the request against each mirror in turn until one responds or all fail.
    private static class MirrorFailoverInterceptor implements Interceptor {
        private final List<String> hosts;

        MirrorFailoverInterceptor(List<String> hosts) {
            this.hosts = hosts;
        }

        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
            Request original = chain.request();
            IOException lastError = null;
            for (String host : hosts) {
                Request request = original.newBuilder()
                        .url(original.url().newBuilder().host(host).build())
                        .build();
                try {
                    return chain.proceed(request);
                } catch (IOException e) {
                    Log.w(TAG, "Mirror " + host + " failed: " + e.getMessage());
                    lastError = e;
                }
            }
            throw lastError != null ? lastError : new IOException("All radio-browser mirrors failed");
        }
    }
}
