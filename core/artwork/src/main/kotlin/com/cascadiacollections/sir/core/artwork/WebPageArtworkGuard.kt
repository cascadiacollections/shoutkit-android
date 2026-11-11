package com.cascadiacollections.sir.core.artwork

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Turns a successful (2xx) artwork response that is really a web page into a failed
 * request.
 *
 * Station artwork URLs are third-party and long-lived. When a broadcaster's site goes
 * into maintenance, or a domain lapses into a parking page, the old image path keeps
 * answering 200 with HTML. KEXP did exactly this in November 2026 after a power outage:
 * every path under kexp.org served the same "Listen Live" page. Without this guard the
 * image loader downloads the page, may cache it as if it were the image, and only then
 * fails to decode it. Failing at the network layer keeps the HTML out of the cache and
 * sends callers straight to their existing fallback (the station monogram or play icon).
 * Once the site serves images again, the next load succeeds because nothing bad was cached.
 *
 * Deliberately narrow: radio-browser favicons routinely arrive as
 * `application/octet-stream`, `text/plain`, or untyped, so only an explicit HTML type or
 * an HTML document body is rejected. Anything else is left for the decoder to judge.
 * Mirrors `NowPlayingArtworkPayload` in the Swift ShoutKit enlistment.
 */
object WebPageArtworkGuard : Interceptor {

    /** Raised for a 2xx response that is a web page rather than an image. */
    class NotAnImageException(url: String) : IOException("Artwork URL returned a web page: $url")

    private const val SNIFF_BYTES = 64L
    private val HTML_MIME_TYPES = setOf("text/html", "application/xhtml+xml")
    private val HTML_PREFIXES = listOf("<!doctype html", "<html")
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return response
        val mimeType = response.body.contentType()?.let { "${it.type}/${it.subtype}" }
        val head = response.peekBody(SNIFF_BYTES).bytes()
        if (isWebPage(mimeType, head)) {
            response.close()
            throw NotAnImageException(chain.request().url.toString())
        }
        return response
    }

    /** Pure classification; [head] is the start of the body (64 bytes is plenty). */
    fun isWebPage(mimeType: String?, head: ByteArray): Boolean {
        if (mimeType?.lowercase() in HTML_MIME_TYPES) return true
        val bytes = if (head.size >= 3 && head.copyOfRange(0, 3).contentEquals(UTF8_BOM)) {
            head.copyOfRange(3, head.size)
        } else {
            head
        }
        val text = bytes.toString(Charsets.ISO_8859_1).trimStart().lowercase()
        return HTML_PREFIXES.any { text.startsWith(it) }
    }
}
