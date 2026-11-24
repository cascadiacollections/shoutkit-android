package com.cascadiacollections.sir

import java.net.URLDecoder
import java.util.Calendar
import java.util.Locale

/**
 * Genres the first-run welcome can offer, in chip order.
 *
 * Values are lowercase radio-browser tags. `christmas` is not one of the curated
 * tags, but radio-browser's `christmas` tag still has well over a page of stations,
 * which is what that chip selects. The genre stays in on-device preferences.
 * Nothing here contacts the network or records an identifier.
 */
val FIRST_RUN_GENRES: Set<String> = linkedSetOf(
    "christmas",
    "talk",
    "rock",
    "news",
    "jazz",
    "country"
)

/** Longer than any allow-listed tag, including a little room for surrounding whitespace. */
internal const val MAX_GENRE_TOKEN_LENGTH = 32

private const val NOVEMBER = 11
private const val DECEMBER = 12
private const val CHRISTMAS_SEASON_START_DAY = 15

/**
 * The allow-listed form of [raw], or null when it is missing, too long, or not a genre
 * the welcome offers. Matching is case-insensitive and ignores surrounding whitespace.
 */
fun allowListedGenre(raw: String?): String? {
    if (raw == null || raw.length > MAX_GENRE_TOKEN_LENGTH) return null
    return raw.trim().lowercase(Locale.ROOT).takeIf { it in FIRST_RUN_GENRES }
}

/**
 * Christmas from November 15 through December 31, otherwise no seasonal preset.
 *
 * [month] is 1-based (January = 1). [java.util.Calendar] is used instead of
 * `java.time`, which is not on the min SDK (24) and this module does not desugar.
 */
fun seasonalFallback(month: Int, dayOfMonth: Int): String? =
    if (month == DECEMBER || (month == NOVEMBER && dayOfMonth >= CHRISTMAS_SEASON_START_DAY)) {
        "christmas"
    } else {
        null
    }

/** [seasonalFallback] for [today]'s month and day. Defaults to the device's current date. */
fun seasonalFallback(today: Calendar = Calendar.getInstance()): String? =
    seasonalFallback(today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))

/**
 * What the welcome preselects: a saved allow-listed genre, then the seasonal default,
 * then nothing.
 */
fun firstRunPreselection(savedGenre: String?, today: Calendar = Calendar.getInstance()): String? =
    allowListedGenre(savedGenre) ?: seasonalFallback(today)

/**
 * Long enough for a real Play referrer (utm parameters plus a click id) and short
 * enough to refuse a hostile payload before it is parsed.
 */
internal const val MAX_INSTALL_REFERRER_LENGTH = 2048

/**
 * The allow-listed `utm_content` of a Play Install Referrer string, or null.
 * The rest of the string — source, campaign, click id, timestamps — is discarded.
 */
internal fun genreFromInstallReferrer(referrer: String?): String? =
    allowListedGenre(decodeUtmContent(utmContent(referrer)))

private fun utmContent(referrer: String?): String? {
    if (referrer.isNullOrEmpty() || referrer.length > MAX_INSTALL_REFERRER_LENGTH) return null
    return referrer.split('&').firstNotNullOfOrNull { part ->
        val separator = part.indexOf('=')
        if (separator > 0 && part.substring(0, separator) == "utm_content") {
            part.substring(separator + 1)
        } else {
            null
        }
    }
}

private fun decodeUtmContent(encoded: String?): String? =
    encoded?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
