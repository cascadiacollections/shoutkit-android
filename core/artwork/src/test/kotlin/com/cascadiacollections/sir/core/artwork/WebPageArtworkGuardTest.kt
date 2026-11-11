package com.cascadiacollections.sir.core.artwork

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

/**
 * A 2xx artwork response that is really a web page (a broadcaster's maintenance page,
 * a parked domain) must fail like a dead link instead of reaching the image decoder.
 */
class WebPageArtworkGuardTest {

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
    private val page = "<!doctype html>\n<html lang=\"en\"><head><title>Listen Live</title>"

    private fun clientAnswering(code: Int, body: ByteArray, contentType: String?): OkHttpClient {
        val server = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody(contentType?.toMediaType()))
                .build()
        }
        return OkHttpClient.Builder()
            .addInterceptor(WebPageArtworkGuard)
            .addInterceptor(server)
            .build()
    }

    private fun fetch(client: OkHttpClient): Response =
        client.newCall(Request.Builder().url("https://station.example/icon.png").build()).execute()

    @Test
    fun `HTML content type is a web page`() {
        assertThat(WebPageArtworkGuard.isWebPage("text/html", png)).isTrue()
        assertThat(WebPageArtworkGuard.isWebPage("TEXT/HTML", png)).isTrue()
        assertThat(WebPageArtworkGuard.isWebPage("application/xhtml+xml", png)).isTrue()
    }

    @Test
    fun `HTML body is a web page whatever the content type claims`() {
        assertThat(WebPageArtworkGuard.isWebPage("image/png", page.toByteArray())).isTrue()
        assertThat(WebPageArtworkGuard.isWebPage(null, "  \n<HTML><head>".toByteArray())).isTrue()
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + page.toByteArray()
        assertThat(WebPageArtworkGuard.isWebPage(null, withBom)).isTrue()
    }

    @Test
    fun `image bytes under a sloppy content type are left to the decoder`() {
        assertThat(WebPageArtworkGuard.isWebPage("image/png", png)).isFalse()
        assertThat(WebPageArtworkGuard.isWebPage("application/octet-stream", png)).isFalse()
        assertThat(WebPageArtworkGuard.isWebPage("text/plain", png)).isFalse()
        assertThat(WebPageArtworkGuard.isWebPage(null, png)).isFalse()
        assertThat(WebPageArtworkGuard.isWebPage(null, ByteArray(0))).isFalse()
    }

    @Test
    fun `SVG is not mistaken for HTML`() {
        val svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()
        assertThat(WebPageArtworkGuard.isWebPage("image/svg+xml", svg)).isFalse()
    }

    @Test
    fun `a 200 web page fails the request`() {
        val client = clientAnswering(200, page.toByteArray(), "text/html; charset=utf-8")
        assertFailure { fetch(client) }.isInstanceOf(WebPageArtworkGuard.NotAnImageException::class)
    }

    @Test
    fun `an image passes through with its body intact`() {
        val client = clientAnswering(200, png, "image/png")
        fetch(client).use { response ->
            assertThat(response.code).isEqualTo(200)
            assertThat(response.body.bytes().toList()).isEqualTo(png.toList())
        }
    }

    @Test
    fun `an error status is left for the caller`() {
        val client = clientAnswering(404, page.toByteArray(), "text/html")
        fetch(client).use { response -> assertThat(response.code).isEqualTo(404) }
    }
}
