package com.cascadiacollections.sir

/**
 * Where a first-run genre can come from the store that installed the app.
 *
 * The Play build reads the Play Install Referrer once. The FOSS build does not:
 * that library is a non-free Google dependency, and GitHub and F-Droid installs
 * have no Play referrer. Implementations return an allow-listed genre or null,
 * never the raw referrer string.
 */
interface CampaignGenreSource {
    suspend fun genreOrNull(): String?
}
