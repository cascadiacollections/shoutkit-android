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

/** The referrer is consulted at most once, and only an allow-listed genre is kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallReferrerGenreTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun `a rock referrer is saved once and a second launch does not read again`() = runBlocking {
        val repo = freshRepo()
        val source = ScriptedSource("rock")

        applyInstallReferrer(repo, source)
        applyInstallReferrer(repo, source)

        assertThat(source.reads).isEqualTo(1)
        assertThat(repo.firstRunGenre.first()).isEqualTo("rock")
        assertThat(repo.installReferrerChecked.first()).isEqualTo(true)
    }

    @Test
    fun `an invalid referrer stores nothing and still counts as checked`() = runBlocking {
        val repo = freshRepo()
        val source = ScriptedSource("utm_source=gads&utm_content=nope&gclid=secret")

        applyInstallReferrer(repo, source)

        assertThat(source.reads).isEqualTo(1)
        assertThat(repo.firstRunGenre.first()).isNull()
        assertThat(repo.installReferrerChecked.first()).isEqualTo(true)
    }

    @Test
    fun `a welcome genre is kept and the referrer is not read`() = runBlocking {
        val repo = freshRepo()
        repo.setFirstRunGenre("jazz")
        val source = ScriptedSource("rock")

        applyInstallReferrer(repo, source)

        assertThat(source.reads).isEqualTo(0)
        assertThat(repo.firstRunGenre.first()).isEqualTo("jazz")
        assertThat(repo.installReferrerChecked.first()).isEqualTo(true)
    }

    @Test
    fun `after the first run the referrer is not read`() = runBlocking {
        val repo = freshRepo()
        repo.setHasCompletedFirstRun(true)
        val source = ScriptedSource("rock")

        applyInstallReferrer(repo, source)

        assertThat(source.reads).isEqualTo(0)
        assertThat(repo.firstRunGenre.first()).isNull()
        assertThat(repo.installReferrerChecked.first()).isEqualTo(true)
    }

    private suspend fun freshRepo(): SettingsRepository {
        val repo = repo()
        repo.setHasCompletedFirstRun(false)
        repo.setInstallReferrerChecked(false)
        repo.setFirstRunGenre(null)
        return repo
    }

    private class ScriptedSource(private val result: String?) : CampaignGenreSource {
        var reads = 0
        override suspend fun genreOrNull(): String? {
            reads += 1
            return result
        }
    }
}
