package com.cascadiacollections.sir

import android.content.Context

/**
 * FOSS build: there is no Play Install Referrer.
 *
 * `com.android.installreferrer` is a non-free Google dependency, so it stays out
 * of this variant and out of the F-Droid build. GitHub and F-Droid installs have
 * no referrer to read. The Play implementation lives in `src/play`.
 */
@Suppress("UNUSED_PARAMETER")
class CampaignGenreSourceImpl(context: Context) : CampaignGenreSource {
    override suspend fun genreOrNull(): String? = null
}
