package com.cascadiacollections.android.audiolevels

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.abs
import org.junit.Test

class PeakHoldTest {
    private val ms = 1_000_000L

    private fun one(level: Float) = AudioLevels(floatArrayOf(level))

    @Test
    fun `cap jumps, holds for 600 ms, then falls at 0_9 per second`() {
        var hold = PeakHold(1).update(one(0.8f), nowNanos = 0)
        assertThat(hold[0]).isEqualTo(0.8f)

        hold = hold.update(one(0.2f), nowNanos = 500 * ms)
        assertThat(hold[0]).isEqualTo(0.8f)

        hold = hold.update(one(0.2f), nowNanos = 700 * ms)
        // Past the hold: falls for the 200 ms since the last update.
        assertThat(abs(hold[0] - (0.8f - 0.9f * 0.2f))).isLessThanOrEqualTo(1e-5f)

        hold = hold.update(one(0.2f), nowNanos = 1_700 * ms)
        // A level catches a falling cap.
        assertThat(hold[0]).isEqualTo(0.2f)
    }

    @Test
    fun `a louder level re-arms the hold`() {
        var hold = PeakHold(1).update(one(0.5f), nowNanos = 0)
        hold = hold.update(one(0.6f), nowNanos = 590 * ms)
        hold = hold.update(one(0.1f), nowNanos = 1_000 * ms)
        assertThat(hold[0]).isEqualTo(0.6f)
    }

    @Test
    fun `a silent frame rests the caps and a band count change starts over`() {
        val hold = PeakHold(2).update(AudioLevels(floatArrayOf(0.9f, 0.4f)), nowNanos = 0)
        assertThat(hold.update(AudioLevels.silent(2), nowNanos = ms).peaks).isEqualTo(listOf(0f, 0f))
        assertThat(hold.update(AudioLevels(floatArrayOf(0.1f, 0.1f, 0.1f)), nowNanos = ms).peaks)
            .isEqualTo(listOf(0.1f, 0.1f, 0.1f))
    }
}
