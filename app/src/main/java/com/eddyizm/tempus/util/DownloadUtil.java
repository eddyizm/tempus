package com.eddyizm.tempus.util;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import androidx.media3.exoplayer.scheduler.Requirements;

import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.service.DownloaderManager;
import com.eddyizm.tempus.subsonic.models.Child;

import java.io.File;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

@UnstableApi
public final class DownloadUtil {

    public static final String DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel";
    public static final String DOWNLOAD_NOTIFICATION_SUCCESSFUL_GROUP = "com.eddyizm.tempus.SuccessfulDownload";
    public static final String DOWNLOAD_NOTIFICATION_FAILED_GROUP = "com.eddyizm.tempus.FailedDownload";

    private static final String STREAMING_CACHE_CONTENT_DIRECTORY = "streaming_cache";
    private static final String DOWNLOAD_CONTENT_DIRECTORY = "downloads";

    private static DataSource.Factory dataSourceFactory;
    private static DataSource.Factory httpDataSourceFactory;
    private static OkHttpClient customHeadersHttpClient;
    private static DatabaseProvider databaseProvider;
    private static File streamingCacheDirectory;
    private static File downloadDirectory;
    private static Cache downloadCache;
    private static SimpleCache streamingCache;
    private static DownloadManager downloadManager;
    private static DownloaderManager downloaderManager;
    private static DownloadNotificationHelper downloadNotificationHelper;

    // Tracks custom-header calls (including body-streaming calls no longer in Dispatcher queues)
    // so TLS policy changes can cancel active connections and prevent reuse under the old policy.
    private static final Set<Call> activeCustomHeaderCalls = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final AtomicLong customHeaderTlsGeneration = new AtomicLong();

    public static boolean useExtensionRenderers() {
        return true;
    }

    public static RenderersFactory buildRenderersFactory(Context context, boolean preferExtensionRenderer) {
        @DefaultRenderersFactory.ExtensionRendererMode int extensionRendererMode =
                useExtensionRenderers()
                        ? (preferExtensionRenderer ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                        : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;

        return new DefaultRenderersFactory(context.getApplicationContext()).setExtensionRendererMode(extensionRendererMode);
    }

    public static synchronized DataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            CookieHandler.setDefault(cookieManager);
            DataSource.Factory serverHttpFactory = new DefaultHttpDataSource
                    .Factory()
                    .setAllowCrossProtocolRedirects(true);
            // Requests to the signed-in server that need its custom headers go through OkHttp,
            // which re-checks the host on every redirect; see CustomHeadersDataSource.
            customHeadersHttpClient = buildCustomHeadersHttpClient();
            DataSource.Factory headersFactory = new CustomHeadersDataSource.Factory(
                    serverHttpFactory,
                    new OkHttpDataSource.Factory(customHeadersHttpClient),
                    url -> !ServerHeaders.getHeadersForActiveServer(url).isEmpty());
            httpDataSourceFactory = new ResolvingDataSource.Factory(headersFactory, ConnectionUtil::moveStreamToAddressInUse);
        }

        return httpDataSourceFactory;
    }

    /**
     * The OkHttp client behind {@link CustomHeadersDataSource}, with the same timeouts as
     * {@link DefaultHttpDataSource} and the client certificate, if one is set up.
     *
     * <p>HTTP/1.1 only, as {@link DefaultHttpDataSource}: over HTTP/2, a reconnect by
     * stall-detecting data sources would share the stuck connection.
     */
    @VisibleForTesting
    static OkHttpClient buildCustomHeadersHttpClient() {
        // Keep hostname verification enabled (default). Trust is narrowed via the user-approved
        // pinned certificate in SelfSignedSslHelper when allow-self-signed is enabled.
        return new OkHttpClient.Builder()
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectTimeout(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .readTimeout(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(ServerHeaders.createInterceptor())
                .eventListener(new TlsPolicyTrackingEventListener(customHeaderTlsGeneration.get()))
                .sslSocketFactory(new DynamicSslSocketFactory(), new DynamicX509TrustManager())
                .build();
    }

    /**
     * TLS policy (self-signed allow/pin, client cert, etc.) changed: prevent reuse of connections
     * established under the previous policy by the cached custom-header client.
     *
     * <p>Evicts idle pooled connections and cancels tracked active calls (including body-streaming
     * calls that may no longer be in dispatcher queues) so they cannot be returned/reused under the
     * new policy. Uses a generation marker to cancel calls that race with invalidation.
     */
    public static synchronized void invalidateTlsConnections() {
        customHeaderTlsGeneration.incrementAndGet();
        if (customHeadersHttpClient != null) {
            customHeadersHttpClient.dispatcher().cancelAll();
            customHeadersHttpClient.connectionPool().evictAll();
        }
        for (Call call : activeCustomHeaderCalls) {
            call.cancel();
        }
        activeCustomHeaderCalls.clear();
    }

    private static final class TlsPolicyTrackingEventListener extends EventListener {
        private final long generationAtCreation;

        TlsPolicyTrackingEventListener(long generationAtCreation) {
            this.generationAtCreation = generationAtCreation;
        }

        @Override
        public void callStart(Call call) {
            // Raced with or created after a policy refresh: do not allow under old policy context.
            if (generationAtCreation != customHeaderTlsGeneration.get()) {
                call.cancel();
                return;
            }
            activeCustomHeaderCalls.add(call);
        }

        @Override
        public void callEnd(Call call) {
            activeCustomHeaderCalls.remove(call);
        }

        @Override
        public void callFailed(Call call, IOException ioe) {
            activeCustomHeaderCalls.remove(call);
        }
    }

    /**
     * Delegates to the current trust manager so a cached client follows later TLS policy refreshes.
     */
    private static final class DynamicX509TrustManager implements X509TrustManager {
        private X509TrustManager current() {
            return SelfSignedSslHelper.INSTANCE.effectiveTrustManager();
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            current().checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            current().checkServerTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return current().getAcceptedIssuers();
        }
    }

    /**
     * Delegates to the current {@link ClientCertManager} socket factory so a cached client follows
     * later TLS policy refreshes (for example self-signed toggles/server switches).
     */
    private static final class DynamicSslSocketFactory extends SSLSocketFactory {
        private SSLSocketFactory current() {
            SSLSocketFactory factory = ClientCertManager.INSTANCE.getSslSocketFactory();
            return factory != null ? factory : (SSLSocketFactory) SSLSocketFactory.getDefault();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return current().getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return current().getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return current().createSocket(s, host, port, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return current().createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return current().createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return current().createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return current().createSocket(address, port, localAddress, localPort);
        }
    }

    public static synchronized DataSource.Factory getHttpDataSourceFactoryForRadio() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        CookieHandler.setDefault(cookieManager);

        // Create a factory with ICY metadata support for radio streams
        Map<String, String> defaultRequestProperties = new HashMap<>();
        defaultRequestProperties.put("Icy-MetaData", "1");
        defaultRequestProperties.put("User-Agent", "Tempus/1.0");

        return new DefaultHttpDataSource
                .Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(defaultRequestProperties);
    }

    public static synchronized DataSource.Factory getUpstreamDataSourceFactory(Context context) {
        DefaultDataSource.Factory upstreamFactory = new DefaultDataSource.Factory(context, getHttpDataSourceFactory());
        dataSourceFactory = buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
        return dataSourceFactory;
    }

    public static synchronized DataSource.Factory getUpstreamDataSourceFactoryForRadio(Context context) {
        DefaultDataSource.Factory upstreamFactory = new DefaultDataSource.Factory(context, getHttpDataSourceFactoryForRadio());
        return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
    }

    public static synchronized DataSource.Factory getCacheDataSourceFactory(Context context) {
        CacheDataSource.Factory streamCacheFactory = new CacheDataSource.Factory()
                .setCache(getStreamingCache(context))
                .setCacheKeyFactory(new StreamingCacheKeyFactory())
                .setUpstreamDataSourceFactory(getUpstreamDataSourceFactory(context));

        ResolvingDataSource.Factory resolvingFactory = new ResolvingDataSource.Factory(
                new StreamingCacheDataSource.Factory(streamCacheFactory),
                dataSpec -> {
                    DataSpec.Builder builder = dataSpec.buildUpon();
                    builder.setFlags(dataSpec.flags & ~DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN);
                    return builder.build();
                }
        );
        dataSourceFactory = buildReadOnlyCacheDataSource(resolvingFactory, getDownloadCache(context));
        return dataSourceFactory;
    }

    /**
     * Writes into the same streaming cache (and with the same keys) the player reads
     * from, but bypasses the download-cache/resolving wrappers of
     * {@link #getCacheDataSourceFactory(Context)}: CacheWriter needs a plain
     * CacheDataSource.
     */
    public static synchronized CacheDataSource.Factory getStreamingCacheWriterFactory(Context context) {
        return new CacheDataSource.Factory()
                .setCache(getStreamingCache(context))
                .setCacheKeyFactory(new StreamingCacheKeyFactory())
                .setUpstreamDataSourceFactory(new DefaultDataSource.Factory(context, getHttpDataSourceFactory()));
    }

    public static synchronized Cache getStreamingCacheForPreload(Context context) {
        return getStreamingCache(context);
    }

    public static synchronized DownloadNotificationHelper getDownloadNotificationHelper(Context context) {
        if (downloadNotificationHelper == null) {
            downloadNotificationHelper = new DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID);
        }

        return downloadNotificationHelper;
    }

    public static synchronized DownloadManager getDownloadManager(Context context) {
        ensureDownloadManagerInitialized(context);
        return downloadManager;
    }

    public static synchronized DownloaderManager getDownloadTracker(Context context) {
        ensureDownloadManagerInitialized(context);
        return downloaderManager;
    }

    private static synchronized Cache getDownloadCache(Context context) {
        if (downloadCache == null) {
            File downloadContentDirectory = new File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY);
            downloadCache = new SimpleCache(downloadContentDirectory, new NoOpCacheEvictor(), getDatabaseProvider(context));
        }

        return downloadCache;
    }

    private static synchronized SimpleCache getStreamingCache(Context context) {
        if (streamingCache == null) {
            File streamingCacheDirectory = new File(getStreamingCacheDirectory(context), STREAMING_CACHE_CONTENT_DIRECTORY);

            streamingCache = new SimpleCache(
                    streamingCacheDirectory,
                    new LeastRecentlyUsedCacheEvictor(Preferences.getStreamingCacheSize() * 1024 * 1024),
                    getDatabaseProvider(context)
            );
        }

        return streamingCache;
    }

    private static synchronized void ensureDownloadManagerInitialized(Context context) {
        if (downloadManager == null) {
            downloadManager = new DownloadManager(
                    context,
                    getDatabaseProvider(context),
                    getDownloadCache(context),
                    getHttpDataSourceFactory(),
                    Executors.newFixedThreadPool(6)
            );

            if (Preferences.isDownloadWifiOnly()) {
                downloadManager.setRequirements(new Requirements(Requirements.NETWORK_UNMETERED));
            }

            downloaderManager = new DownloaderManager(context, getHttpDataSourceFactory(), downloadManager);
        }
    }

    private static synchronized DatabaseProvider getDatabaseProvider(Context context) {
        if (databaseProvider == null) {
            databaseProvider = new StandaloneDatabaseProvider(context);
        }

        return databaseProvider;
    }

    private static synchronized File getStreamingCacheDirectory(Context context) {
        if (streamingCacheDirectory == null) {
            if (Preferences.getStreamingCacheStoragePreference() == 0) {
                streamingCacheDirectory = context.getExternalFilesDirs(null)[0];
                // workaround for crdroid degoogled android 16 sometimes unable to write to the
                // default external files dir, fall back to internal dir if we can't write
                if (streamingCacheDirectory == null || !streamingCacheDirectory.canWrite()) {
                    streamingCacheDirectory = context.getFilesDir();
                }
            } else {
                try {
                    streamingCacheDirectory = context.getExternalFilesDirs(null)[1];
                } catch (Exception exception) {
                    streamingCacheDirectory = context.getExternalFilesDirs(null)[0];
                    Preferences.setStreamingCacheStoragePreference(0);
                }

            }
        }

        return streamingCacheDirectory;
    }

    private static synchronized File getDownloadDirectory(Context context) {
        if (downloadDirectory == null) {
            int pref = Preferences.getDownloadStoragePreference();
            if (pref == 0) {
                downloadDirectory = context.getExternalFilesDirs(null)[0];
                // workaround for crdroid degoogled android 16 sometimes unable to write to the
                // default external files dir, fall back to internal dir if we can't write
                if (downloadDirectory == null || !downloadDirectory.canWrite()) {
                    downloadDirectory = context.getFilesDir();
                }
            } else if (pref == 1) {
                try {
                    downloadDirectory = context.getExternalFilesDirs(null)[1];
                } catch (Exception exception) {
                    downloadDirectory = context.getExternalFilesDirs(null)[0];
                    Preferences.setDownloadStoragePreference(0);
                }
            } else {
                downloadDirectory = context.getExternalFilesDirs(null)[0];
            }
        }

        return downloadDirectory;
    }

    private static CacheDataSource.Factory buildReadOnlyCacheDataSource(DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static synchronized void eraseDownloadFolder(Context context) {
        File directory = getDownloadDirectory(context);

        ArrayList<File> files = listFiles(directory, new ArrayList<>());

        for (File file : files) {
            file.delete();
        }
    }

    private static synchronized ArrayList<File> listFiles(File directory, ArrayList<File> files) {
        if (directory.isDirectory()) {
            File[] list = directory.listFiles();

            if (list != null) {
                for (File file : list) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".exo")) {
                        files.add(file);
                    } else if (file.isDirectory()) {
                        listFiles(file, files);
                    }
                }
            }
        }

        return files;
    }

    public static synchronized long getStreamingCacheSize(Context context) {
        return getStreamingCache(context).getCacheSpace();
    }

    // Batches the download service could not be started for. Main thread only.
    private static final List<Runnable> deferredBatches = new ArrayList<>();

    /**
     * Queues the batches {@link #downloadMissing} kept because Android refused to start the
     * download service. Call when the app is back on screen. A batch is dropped if every download
     * was removed since it was kept.
     */
    public static void queueDeferredDownloads() {
        if (deferredBatches.isEmpty()) return;

        List<Runnable> batches = new ArrayList<>(deferredBatches);
        deferredBatches.clear();
        batches.forEach(Runnable::run);
    }

    /**
     * Queues the tracks neither on the device nor already asked for, and reports on the main thread
     * how many reached the download service. When Android refuses to start the service, the batch is
     * kept for {@link #queueDeferredDownloads()} and reported then. Call on the main thread.
     */
    public static void downloadMissing(Context context, List<Child> songs, String playlistId, String playlistName, IntConsumer onQueued) {
        // A playlist can list the same track twice.
        Map<String, Child> byId = new LinkedHashMap<>();
        for (Child song : songs) byId.putIfAbsent(song.getId(), song);
        Context appContext = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());

        if (Preferences.getDownloadDirectoryUri() == null) {
            DownloaderManager manager = getDownloadTracker(context);
            List<Child> missing = byId.values().stream()
                    .filter(song -> !manager.isDownloaded(song.getId()) && !manager.isRequested(song.getId()))
                    .collect(Collectors.toList());
            // Queued right here, while the app is on screen: the download service has to come to the
            // foreground now, and Android refuses that a few seconds after the app leaves the screen.
            int sent = manager.download(
                    MappingUtil.mapDownloads(missing),
                    missing.stream().map(child -> {
                        Download toDownload = new Download(child);
                        toDownload.setPlaylistId(playlistId);
                        toDownload.setPlaylistName(playlistName);
                        return toDownload;
                    }).collect(Collectors.toList())
            );
            if (sent == 0 && !missing.isEmpty()) {
                // Android refused to start the service, as it does while the app is in the background.
                int generation = DownloaderManager.getRemovalGeneration();
                deferredBatches.add(() -> {
                    if (generation == DownloaderManager.getRemovalGeneration()) {
                        downloadMissing(appContext, songs, playlistId, playlistName, onQueued);
                    }
                });
                return;
            }
            onQueued.accept(sent);
        } else {
            // The folder index only builds off main. While a rebuild runs it is empty and every track reads as
            // missing, and the writer's own size check is what keeps a file already there from being written again.
            new Thread(() -> {
                ExternalAudioReader.ensureCache();
                List<Child> missing = byId.values().stream()
                        .filter(song -> ExternalAudioReader.getUri(song) == null && !ExternalAudioWriter.isPending(song.getId()))
                        .collect(Collectors.toList());
                missing.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(appContext, child, playlistId, playlistName));
                main.post(() -> onQueued.accept(missing.size()));
            }).start();
        }
    }

    public static Notification buildGroupSummaryNotification(Context context, String channelId, String groupId, int icon, String title) {
        return new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setSmallIcon(icon)
                .setGroup(groupId)
                .setGroupSummary(true)
                .build();
    }
}
