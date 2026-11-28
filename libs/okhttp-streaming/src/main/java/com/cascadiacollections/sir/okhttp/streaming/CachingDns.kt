package com.cascadiacollections.sir.okhttp.streaming

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Dns

/**
 * DNS resolver that caches lookups for [ttlMs] to avoid repeated resolution on reconnect.
 * Results are sorted to prefer IPv4 for faster connection on mobile networks.
 */
internal class CachingDns(private val ttlMs: Long = DEFAULT_TTL_MS) : Dns {
    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        return cache[hostname]
            ?.takeIf { now - it.second < ttlMs }
            ?.first
            ?: Dns.SYSTEM.lookup(hostname)
                .sortedBy { it !is Inet4Address }
                .also { cache[hostname] = it to now }
    }

    private companion object {
        const val DEFAULT_TTL_MINUTES = 5L
        val DEFAULT_TTL_MS = TimeUnit.MINUTES.toMillis(DEFAULT_TTL_MINUTES)
    }
}
