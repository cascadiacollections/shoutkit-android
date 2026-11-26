package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Play build's Settings link has to open the Play privacy page. The page in
 * src/main describes the FOSS build, which excludes Firebase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayPrivacyPolicyUrlTest {

    @Test
    fun `play settings open the play privacy page`() {
        val url = RuntimeEnvironment.getApplication().getString(R.string.privacy_policy_url)

        assertThat(url).isEqualTo(
            "https://cascadiacollections.github.io/shoutkit-android/privacy/play/"
        )
    }
}
