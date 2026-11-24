package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * On the first launch, saves an allow-listed genre from [source] when nothing
 * else has set one. A welcome link or an existing preset wins. The check is
 * recorded either way, so the referrer is read at most once. Only the genre
 * is stored.
 */
internal suspend fun applyInstallReferrer(repository: SettingsRepository, source: CampaignGenreSource) {
    if (repository.installReferrerChecked.first()) return
    val mayPreset = !repository.hasCompletedFirstRun.first() && repository.firstRunGenre.first() == null
    if (mayPreset) {
        allowListedGenre(source.genreOrNull())?.let { repository.setFirstRunGenre(it) }
    }
    repository.setInstallReferrerChecked(true)
}
