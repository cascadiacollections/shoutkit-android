package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Where a `sir://welcome` genre goes: the chips before first run, Browse after. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeGenreTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `before first run the genre is saved and not offered to browse`() = runBlocking {
        val repo = repo()
        repo.setHasCompletedFirstRun(false)
        repo.setFirstRunGenre(null)
        repo.consumeBrowseGenre()

        applyWelcomeGenre("talk", repo)

        assertThat(repo.firstRunGenre.first()).isEqualTo("talk")
        assertThat(repo.browseGenreRequest.value).isNull()
    }

    @Test
    fun `after first run the genre is offered and the saved preset stays untouched`() = runBlocking {
        val repo = repo()
        repo.setHasCompletedFirstRun(true)
        repo.setFirstRunGenre("jazz")
        repo.consumeBrowseGenre()

        applyWelcomeGenre("country", repo)

        assertThat(repo.browseGenreRequest.value).isEqualTo("country")
        assertThat(repo.firstRunGenre.first()).isEqualTo("jazz")
        repo.consumeBrowseGenre()
        assertThat(repo.browseGenreRequest.value).isNull()
    }

    @Test
    fun `an unknown genre changes nothing`() = runBlocking {
        val repo = repo()
        repo.setHasCompletedFirstRun(false)
        repo.setFirstRunGenre(null)
        repo.consumeBrowseGenre()

        applyWelcomeGenre("pop", repo)
        applyWelcomeGenre("x".repeat(80), repo)
        applyWelcomeGenre(null, repo)

        assertThat(repo.firstRunGenre.first()).isNull()
        assertThat(repo.browseGenreRequest.value).isNull()
    }
}
