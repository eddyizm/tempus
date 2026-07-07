package com.cappielloantonio.tempo.subsonic

import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.BuildConfig
import com.cappielloantonio.tempo.subsonic.utils.CacheUtil
import com.cappielloantonio.tempo.subsonic.utils.EmptyDateTypeAdapter
import com.cappielloantonio.tempo.util.ClientCertManager
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Date
import java.util.concurrent.TimeUnit

class RetrofitClient(subsonic: Subsonic) {
    val retrofit: Retrofit

    init {
        val gson = GsonBuilder()
            .registerTypeAdapter(Date::class.java, EmptyDateTypeAdapter())
            .setLenient()
            .create()

        retrofit = Retrofit.Builder()
            .baseUrl(sanitizeBaseUrl(subsonic.url))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(getOkHttpClient())
            .build()
    }

    /**
     * Retrofit requires a syntactically valid base URL with an http/https scheme AND a parseable
     * host, throwing IllegalArgumentException otherwise. Two distinct failure modes reach here:
     *
     *  - unconfigured / blank / scheme-less address (timed-out first login, half-configured local
     *    address) -> "Expected URL scheme 'http' or 'https'" (issues #776, #758)
     *  - a scheme is present but the host is malformed, e.g. a typo'd FQDN with a space
     *    ("http://navi.example com") -> "Invalid URL host" (issue #795)
     *
     * Parsing with OkHttp (the same parser Retrofit uses) catches both: an unparseable address
     * yields null, so we fall back to an unreachable placeholder. The client then builds and
     * requests fail through the normal onFailure callbacks instead of taking down the process.
     */
    private fun sanitizeBaseUrl(url: String?): String {
        val trimmed = url?.trim().orEmpty()
        val parsed = trimmed.toHttpUrlOrNull()
        return if (parsed != null && parsed.host.isNotEmpty()) {
            trimmed
        } else {
            PLACEHOLDER_BASE_URL
        }
    }

    /** Constants for [RetrofitClient]. */
    companion object {
        /** Syntactically valid but unreachable; used only when no usable server URL is configured. */
        private const val PLACEHOLDER_BASE_URL = "https://localhost/rest/"
    }

    private fun getOkHttpClient(): OkHttpClient {
        val cacheUtil = CacheUtil(60, 60 * 60 * 24 * 30)

        // BrowsingClient 60
        // MediaAnnotationClient 0
        // MediaLibraryScanningClient 0
        // MediaRetrievalClient 0
        // PlaylistClient 0
        // PodcastClient 60
        // SearchClient 60
        // SystemClient 60
        // AlbumSongListClient 60

        return OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.MINUTES)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(getHttpLoggingInterceptor())
            .addInterceptor(cacheUtil.offlineInterceptor)
            // .addNetworkInterceptor(cacheUtil.onlineInterceptor)
            .cache(getCache())
            .setupSsl()
            .build()
    }

    private fun getHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor()
        if (BuildConfig.DEBUG) {
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        } else {
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE)
        }
        return loggingInterceptor
    }

    private fun getCache(): Cache {
        val cacheSize = 10 * 1024 * 1024
        return Cache(App.getContext().cacheDir, cacheSize.toLong())
    }

    private fun OkHttpClient.Builder.setupSsl(): OkHttpClient.Builder {
        ClientCertManager.sslSocketFactory?.let { sslSocketFactory ->
            sslSocketFactory(sslSocketFactory, ClientCertManager.trustManager)
        }
        return this
    }
}