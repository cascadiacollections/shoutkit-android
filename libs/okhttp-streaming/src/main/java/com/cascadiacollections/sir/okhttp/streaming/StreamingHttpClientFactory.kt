package com.cascadiacollections.sir.okhttp.streaming

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * Creates a pre-configured [OkHttpClient.Builder] optimized for live audio streaming:
 * - Connection pooling for instant reconnects on network switches
 * - HTTP/2 with HTTP/1.1 fallback
 * - DNS caching for faster reconnects
 * - Modern TLS only
 * - No overall call timeout (streaming is open-ended)
 *
 * Returns a [Builder][OkHttpClient.Builder] so callers can add module-specific
 * customizations (e.g. debug logging interceptor) before building.
 */
object StreamingHttpClientFactory {

    private const val MAX_IDLE_CONNECTIONS = 2
    private const val KEEP_ALIVE_MINUTES = 5L
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 0L

    fun newBuilder(): OkHttpClient.Builder {
        val connectionPool = ConnectionPool(
            maxIdleConnections = MAX_IDLE_CONNECTIONS,
            keepAliveDuration = KEEP_ALIVE_MINUTES,
            timeUnit = TimeUnit.MINUTES
        )

        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dns(CachingDns())
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
    }
}
