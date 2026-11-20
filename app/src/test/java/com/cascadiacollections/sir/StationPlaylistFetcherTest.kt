package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.playback.StreamEndpoint
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [StationPlaylistFetcher] resolves `.pls`/`.m3u` station URLs before playback. Every
 * failure has to fall back to the original URL rather than throw, and the body read has
 * to stop at its cap so a server that streams audio at a playlist path cannot hang it.
 * Responses come from an interceptor, so no socket is opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StationPlaylistFetcherTest {

    private val playlistUrl = "https://radio.example/station.pls"

    private var lastRequest: Request? = null

    private fun fetcher(respond: (Request) -> Response): StationPlaylistFetcher {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    lastRequest = chain.request()
                    respond(chain.request())
                }
            )
            .build()
        return StationPlaylistFetcher(client, USER_AGENT)
    }

    private fun response(request: Request, code: Int = 200, body: ResponseBody): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status $code")
        .body(body)
        .build()

    private fun textResponse(request: Request, text: String, code: Int = 200, type: String = "audio/x-scpls") =
        response(request, code, text.toResponseBody(type.toMediaType()))

    @Test
    fun `pls entry resolves to the stream it names`() = runTest {
        val pls = "[playlist]\nNumberOfEntries=1\nFile1=https://stream.example/live.mp3\nTitle1=Live\n"
        val endpoint = fetcher { textResponse(it, pls) }.resolve(playlistUrl)
        assertThat(endpoint).isEqualTo(StreamEndpoint("https://stream.example/live.mp3"))
    }

    @Test
    fun `m3u entry pointing at HLS is flagged as HLS`() = runTest {
        val m3u = "#EXTM3U\n#EXTINF:-1,Live\nhttps://stream.example/live/index.m3u8\n"
        val endpoint = fetcher { textResponse(it, m3u, type = "audio/x-mpegurl") }
            .resolve("https://radio.example/station.m3u")
        assertThat(endpoint.url).isEqualTo("https://stream.example/live/index.m3u8")
        assertThat(endpoint.isHls).isTrue()
    }

    @Test
    fun `request carries the configured user agent`() = runTest {
        fetcher { textResponse(it, "") }.resolve(playlistUrl)
        assertThat(lastRequest?.header("User-Agent")).isEqualTo(USER_AGENT)
    }

    @Test
    fun `HTTP error falls back to the original url`() = runTest {
        val endpoint = fetcher { textResponse(it, "File1=https://stream.example/x.mp3", code = 404) }
            .resolve(playlistUrl)
        assertThat(endpoint).isEqualTo(StreamEndpoint(playlistUrl))
    }

    @Test
    fun `network failure falls back to the original url`() = runTest {
        val endpoint = fetcher { throw IOException("connection reset") }.resolve(playlistUrl)
        assertThat(endpoint).isEqualTo(StreamEndpoint(playlistUrl))
    }

    @Test
    fun `network security refusal falls back to the original url`() = runTest {
        val endpoint = fetcher { throw SecurityException("cleartext not permitted") }.resolve(playlistUrl)
        assertThat(endpoint).isEqualTo(StreamEndpoint(playlistUrl))
    }

    @Test
    fun `non-http url is not fetched and falls back to itself`() = runTest {
        val url = "content://media/station.m3u"
        val endpoint = fetcher { error("must not be fetched") }.resolve(url)
        assertThat(endpoint).isEqualTo(StreamEndpoint(url))
        assertThat(lastRequest == null).isTrue()
    }

    @Test
    fun `body without a stream entry falls back to the original url`() = runTest {
        val endpoint = fetcher { textResponse(it, "<html>not a playlist</html>", type = "text/html") }
            .resolve(playlistUrl)
        assertThat(endpoint).isEqualTo(StreamEndpoint(playlistUrl))
    }

    @Test
    fun `endless body is read only up to the cap`() = runTest {
        val source = EndlessSource()
        val body = object : ResponseBody() {
            override fun contentType() = "audio/mpeg".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = source.buffer()
        }
        val endpoint = fetcher { response(it, body = body) }.resolve(playlistUrl)
        // Audio bytes are not a playlist, so the original URL is played.
        assertThat(endpoint).isEqualTo(StreamEndpoint(playlistUrl))
        // 64 KiB cap, plus at most one buffered segment of read-ahead.
        assertThat(source.bytesServed).isLessThanOrEqualTo(64L * 1024 + 8192)
        assertThat(source.closed).isTrue()
    }

    @Test
    fun `charset from the content type decodes the body`() = runTest {
        val pls = "[playlist]\nFile1=https://stream.example/café.mp3\n"
        val body = pls.toByteArray(Charsets.ISO_8859_1)
            .toResponseBody("audio/x-scpls; charset=ISO-8859-1".toMediaType())
        val endpoint = fetcher { response(it, body = body) }.resolve(playlistUrl)
        assertThat(endpoint.url).isEqualTo("https://stream.example/café.mp3")
        assertThat(endpoint.isHls).isFalse()
    }

    /** Serves zero bytes forever, counting how many were asked for. */
    private class EndlessSource : Source {
        var bytesServed = 0L
        var closed = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            val n = minOf(byteCount, 8192L)
            sink.write(ByteArray(n.toInt()))
            bytesServed += n
            return n
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val USER_AGENT = "SIR Android/test"
    }
}
