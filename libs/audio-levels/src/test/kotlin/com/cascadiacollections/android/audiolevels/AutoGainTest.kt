package com.cascadiacollections.android.audiolevels

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import kotlin.math.abs
import org.junit.Test

/** Mirrors the Swift package's AutoGainTests. */
class AutoGainTest {
    private val second = 1_000_000_000L

    @Test
    fun `quiet material is lifted but never past the floor reference`() {
        val (quiet, gain) = AutoGain().apply(AudioLevels(floatArrayOf(0.15f, 0.06f)), nowNanos = 0)
        assertThat(abs(quiet[0] - 0.5f)).isLessThanOrEqualTo(1e-4f)
        assertThat(gain.reference).isEqualTo(AutoGain.MINIMUM_REFERENCE)
    }

    @Test
    fun `a loud frame fills the meter, then the reference decays slowly`() {
        val (loud, afterLoud) = AutoGain().apply(AudioLevels(floatArrayOf(0.8f, 0.4f)), nowNanos = 0)
        assertThat(loud.toList()).isEqualTo(listOf(1f, 0.5f))

        val afterOne = afterLoud.apply(AudioLevels(floatArrayOf(0.2f)), nowNanos = second).gain
        assertThat(abs(afterOne.reference - (0.8f - AutoGain.DECAY_RATE))).isLessThanOrEqualTo(1e-4f)

        val afterTen = afterOne.apply(AudioLevels(floatArrayOf(0.2f)), nowNanos = 10 * second).gain
        assertThat(afterTen.reference).isEqualTo(AutoGain.MINIMUM_REFERENCE)
    }

    @Test
    fun `silence stays silent`() {
        assertThat(AutoGain().apply(AudioLevels.silent(), nowNanos = 0).levels.isSilent).isTrue()
    }
}
