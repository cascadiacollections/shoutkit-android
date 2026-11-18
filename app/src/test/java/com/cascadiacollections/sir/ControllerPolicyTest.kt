package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class ControllerPolicyTest {

    private val own = "com.cascadiacollections.sir"

    private fun allowed(pkg: String, trusted: Boolean = false, system: Boolean = false): Boolean =
        ControllerPolicy.isAllowed(pkg, own, trusted, system)

    @Test
    fun `own package is allowed`() {
        assertThat(allowed(own)).isTrue()
    }

    @Test
    fun `android auto, assistant and wear are allowed by package`() {
        assertThat(allowed("com.google.android.projection.gearhead")).isTrue()
        assertThat(allowed("com.google.android.googlequicksearchbox")).isTrue()
        assertThat(allowed("com.google.android.wearable.app")).isTrue()
    }

    @Test
    fun `trusted controller is allowed`() {
        assertThat(allowed("com.example.launcher", trusted = true)).isTrue()
    }

    @Test
    fun `system surface is allowed`() {
        assertThat(allowed("com.android.car.media", system = true)).isTrue()
    }

    @Test
    fun `unknown untrusted app is rejected`() {
        assertThat(allowed("com.example.other")).isFalse()
    }

    @Test
    fun `package that only starts with ours is rejected`() {
        assertThat(allowed("$own.evil")).isFalse()
    }
}
