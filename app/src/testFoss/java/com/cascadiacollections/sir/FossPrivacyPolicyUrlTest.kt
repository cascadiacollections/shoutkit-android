package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The FOSS build's Settings link stays on the FOSS privacy page. A Play URL in src/main
 * would describe Firebase to a build that does not include it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FossPrivacyPolicyUrlTest {

    @Test
    fun `foss settings open the foss privacy page`() {
        val url = RuntimeEnvironment.getApplication().getString(R.string.privacy_policy_url)

        assertThat(url).isEqualTo(
            "https://cascadiacollections.github.io/shoutkit-android/privacy/"
        )
    }
}
