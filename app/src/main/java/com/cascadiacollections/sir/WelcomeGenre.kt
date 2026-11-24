package com.cascadiacollections.sir

import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Before the first run, [genre] is saved for the welcome chips. Afterwards it is
 * offered for Browse. Unknown values are dropped. Nothing but an allow-listed
 * genre is stored, and the post-first-run offer is not written to DataStore.
 */
internal suspend fun applyWelcomeGenre(genre: String?, repository: SettingsRepository) {
    val allowed = allowListedGenre(genre) ?: return
    if (repository.hasCompletedFirstRun.first()) {
        repository.offerBrowseGenre(allowed)
    } else {
        repository.setFirstRunGenre(allowed)
    }
}
