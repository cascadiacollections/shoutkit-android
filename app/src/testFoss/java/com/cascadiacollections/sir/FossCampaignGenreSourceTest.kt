package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The FOSS variant must not read a Play Install Referrer. This compiles against
 * `src/foss` only, so a Play client moved into `main` fails this source set.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FossCampaignGenreSourceTest {

    @Test
    fun `foss install referrer source is a no-op`() = runBlocking {
        val source = CampaignGenreSourceImpl(RuntimeEnvironment.getApplication())

        assertThat(source.genreOrNull()).isNull()
    }
}
