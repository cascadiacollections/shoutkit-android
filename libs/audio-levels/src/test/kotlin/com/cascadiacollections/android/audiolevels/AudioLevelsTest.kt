package com.cascadiacollections.android.audiolevels

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import org.junit.Test

class AudioLevelsTest {
    @Test
    fun `resampled folds groups by maximum`() {
        val levels = AudioLevels(List(24) { it / 24f })
        assertThat(levels.resampled(4).toList()).containsExactly(5 / 24f, 11 / 24f, 17 / 24f, 23 / 24f)
    }

    @Test
    fun `resampled to a count that does not divide evenly covers every band once`() {
        val levels = AudioLevels(List(24) { if (it == 23) 1f else 0f })
        val folded = levels.resampled(5)
        assertThat(folded.size).isEqualTo(5)
        assertThat(folded[4]).isEqualTo(1f)
        // Groups are [0,4) [4,9) [9,14) [14,19) [19,24).
        assertThat(AudioLevels(List(24) { if (it == 4) 1f else 0f }).resampled(5).toList())
            .containsExactly(0f, 1f, 0f, 0f, 0f)
    }

    @Test
    fun `resampled to as many or more bands is the same frame, and to none is empty`() {
        val levels = AudioLevels(List(24) { 0.5f })
        assertThat(levels.resampled(24)).isSameInstanceAs(levels)
        assertThat(levels.resampled(30)).isSameInstanceAs(levels)
        assertThat(levels.resampled(0).size).isEqualTo(0)
    }

    @Test
    fun `equality is by content and the frame is immutable`() {
        val source = floatArrayOf(0.1f, 0.2f)
        val levels = AudioLevels(source)
        source[0] = 1f
        levels.toFloatArray()[1] = 1f
        assertThat(levels).isEqualTo(AudioLevels(listOf(0.1f, 0.2f)))
        assertThat(AudioLevels.silent().isSilent).isTrue()
        assertThat(levels.isSilent).isFalse()
    }
}
