package com.eddyizm.tempus.util

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds a server's headers (see [ServerHeaders]) to OkHttp requests.
 *
 * Install it with `addNetworkInterceptor`: a network interceptor runs once per network request,
 * including every redirect hop, so [headersFor] is asked again for each URL and the headers do
 * not follow a redirect to another host. It also runs after application interceptors such as
 * `HttpLoggingInterceptor`, so the values are not logged.
 */
class CustomHeadersInterceptor(
    private val headersFor: (url: String) -> Map<String, String>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = headersFor(request.url.toString())
        if (headers.isEmpty()) return chain.proceed(request)

        val builder = request.newBuilder()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return chain.proceed(builder.build())
    }

    companion object {
        /** Headers of the signed-in server, looked up per request so a server switch applies at once. */
        @JvmStatic
        fun forActiveServer(): CustomHeadersInterceptor = CustomHeadersInterceptor(ServerHeaders::forActiveServer)
    }
}
