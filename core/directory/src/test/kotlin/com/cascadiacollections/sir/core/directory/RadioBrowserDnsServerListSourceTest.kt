package com.cascadiacollections.sir.core.directory

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import java.io.IOException
import java.net.InetAddress
import org.junit.Test

class RadioBrowserDnsServerListSourceTest {
    private val ipv4 = InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4))
    private val ipv6 = InetAddress.getByAddress(ByteArray(16).apply { this[15] = 1 })

    @Test
    fun `keeps distinct mirrors advertised through DNS`() {
        val source = RadioBrowserDnsServerListSource(
            resolve = { listOf(ipv4, ipv6) },
            reverse = { if (it == ipv4) "de1.api.radio-browser.info" else "fi1.api.radio-browser.info" }
        )
        assertThat(source.serverNames()).containsExactly("de1.api.radio-browser.info", "fi1.api.radio-browser.info")
    }

    @Test
    fun `discovers every address family and deduplicates mirror names`() {
        val source = RadioBrowserDnsServerListSource(
            resolve = { host ->
                assertThat(host).isEqualTo("all.api.radio-browser.info")
                listOf(ipv4, ipv4, ipv6)
            },
            reverse = { "DE1.api.radio-browser.info." }
        )
        assertThat(source.serverNames()).containsExactly("de1.api.radio-browser.info")
    }

    @Test
    fun `a failed reverse lookup does not discard other mirrors`() {
        val source = RadioBrowserDnsServerListSource(
            resolve = { listOf(ipv4, ipv6) },
            reverse = { if (it == ipv4) throw IOException("PTR failed") else "fi1.api.radio-browser.info" }
        )
        assertThat(source.serverNames()).containsExactly("fi1.api.radio-browser.info")
    }

    @Test
    fun `rejects IP literals and unrelated PTR names`() {
        val source = RadioBrowserDnsServerListSource(
            resolve = { listOf(ipv4, ipv6) },
            reverse = { if (it == ipv4) "1.2.3.4" else "api.radio-browser.info.evil.example" }
        )
        assertFailure { source.serverNames() }.isInstanceOf<IOException>()
    }

    @Test
    fun `successful DNS discovery does not contact HTTP discovery`() {
        var httpCalls = 0
        val source = FallbackServerListSource(
            RadioBrowserDnsServerListSource(resolve = { listOf(ipv4) }, reverse = { "de1.api.radio-browser.info" }),
            ServerListSource {
                httpCalls++
                throw IOException("HTTP endpoint down")
            }
        )
        assertThat(source.serverNames()).containsExactly("de1.api.radio-browser.info")
        assertThat(httpCalls).isEqualTo(0)
    }

    @Test
    fun `DNS failure falls back to HTTP discovery`() {
        val source = FallbackServerListSource(
            RadioBrowserDnsServerListSource(resolve = { throw IOException("DNS failed") }),
            ServerListSource { listOf("fi1.api.radio-browser.info") }
        )
        assertThat(source.serverNames()).containsExactly("fi1.api.radio-browser.info")
    }

    @Test
    fun `unusable PTR answers fall back to HTTP discovery`() {
        val source = FallbackServerListSource(
            RadioBrowserDnsServerListSource(resolve = { listOf(ipv4) }, reverse = { "1.2.3.4" }),
            ServerListSource { listOf("fi1.api.radio-browser.info") }
        )
        assertThat(source.serverNames()).containsExactly("fi1.api.radio-browser.info")
    }

    @Test
    fun `failure of every discovery source is reported`() {
        val source = FallbackServerListSource(ServerListSource { throw IOException("DNS failed") })
        assertFailure { source.serverNames() }.isInstanceOf<IOException>()
    }
}
