package com.eddyizm.tempus.glide;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import com.eddyizm.tempus.util.ClientCertManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class IPv6StringLoader implements ModelLoader<String, InputStream> {
    private static final int DEFAULT_TIMEOUT_MS = 2500;
    private final OkHttpClient client;

    private IPv6StringLoader(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public boolean handles(@NonNull String model) {
        return model.startsWith("http://") || model.startsWith("https://");
    }

    @Override
    public LoadData<InputStream> buildLoadData(@NonNull String model, int width, int height, @NonNull Options options) {
        if (!handles(model)) {
            return null;
        }
        return new LoadData<>(new ObjectKey(model), new IPv6StreamFetcher(client, model));
    }

    private static class IPv6StreamFetcher implements DataFetcher<InputStream> {
        private final OkHttpClient client;
        private final String model;
        private Call call;
        private Response response;

        IPv6StreamFetcher(OkHttpClient client, String model) {
            this.client = client;
            this.model = model;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            try {
                call = client.newCall(new Request.Builder().url(model).build());
                response = call.execute();
                if (!response.isSuccessful()) {
                    callback.onLoadFailed(new IOException("Request failed with status code: " + response.code()));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    callback.onLoadFailed(new IOException("Image response has no body"));
                    return;
                }
                callback.onDataReady(body.byteStream());
            } catch (IOException | IllegalArgumentException e) {
                callback.onLoadFailed(e);
            }
        }

        @Override
        public void cleanup() {
            if (response != null) {
                response.close();
            }
        }

        @Override
        public void cancel() {
            if (call != null) {
                call.cancel();
            }
        }

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.REMOTE;
        }
    }

    public static class Factory implements ModelLoaderFactory<String, InputStream> {
        private final OkHttpClient client;

        public Factory() {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (ClientCertManager.INSTANCE.getSslSocketFactory() != null) {
                builder.sslSocketFactory(ClientCertManager.INSTANCE.getSslSocketFactory(),
                        ClientCertManager.INSTANCE.getTrustManager());
            }
            client = builder.build();
        }

        @NonNull
        @Override
        public ModelLoader<String, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new IPv6StringLoader(client);
        }

        @Override
        public void teardown() {
            // No-op
        }
    }
}
