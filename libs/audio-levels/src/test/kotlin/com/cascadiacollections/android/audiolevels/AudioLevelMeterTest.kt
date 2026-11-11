package com.cascadiacollections.android.audiolevels

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class AudioLevelMeterTest {
    private fun toneBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(1024 * 4).order(ByteOrder.nativeOrder())
        for (frame in 0 until 1024) buffer.putFloat((0.5 * sin(2 * PI * 1000 * frame / 48_000)).toFloat())
        buffer.flip()
        return buffer
    }

    @Test
    fun `publishes at most once per interval, smoothing from rest`() {
        var now = 0L
        val frames = mutableListOf<AudioLevels>()
        val meter = AudioLevelMeter(nanoTime = { now }) { frames += it }
        val raw = checkNotNull(AudioSpectrumAnalyzer().levels(toneBuffer(), PcmEncoding.PCM_FLOAT, 1, 48_000))

        meter.process(toneBuffer(), PcmEncoding.PCM_FLOAT, 1, 48_000)
        now += 10_000_000L
        meter.process(toneBuffer(), PcmEncoding.PCM_FLOAT, 1, 48_000)
        assertThat(frames.size).isEqualTo(1)
        // First frame closes 70% of the gap from zero.
        for (band in 0 until raw.size) assertThat(frames[0][band]).isEqualTo(raw[band] * AudioLevelMeter.ATTACK)

        now += AudioLevelMeter.DEFAULT_MINIMUM_INTERVAL_NANOS
        meter.process(toneBuffer(), PcmEncoding.PCM_FLOAT, 1, 48_000)
        assertThat(frames.size).isEqualTo(2)
        val loudest = (0 until raw.size).maxBy { raw[it] }
        assertThat(frames[1][loudest]).isGreaterThan(frames[0][loudest])
    }

    @Test
    fun `release is slower than attack`() {
        val up = AudioLevelMeter.smooth(floatArrayOf(0f), floatArrayOf(1f))[0]
        val down = 1f - AudioLevelMeter.smooth(floatArrayOf(1f), floatArrayOf(0f))[0]
        assertThat(down).isLessThan(up)
    }

    @Test
    fun `the flow taps only while collected`() = runTest {
        val taps = AudioTapPluginSet()
        assertThat(taps.isEmpty).isTrue()
        val collector = launch { taps.levels(minimumIntervalNanos = 0).first() }
        while (taps.isEmpty) yield()
        assertThat(taps.size).isEqualTo(1)
        taps.dispatch(toneBuffer(), PcmEncoding.PCM_FLOAT, 1, 48_000)
        collector.join()
        assertThat(taps.isEmpty).isTrue()
    }

    @Test
    fun `a set ignores duplicates and survives removal during dispatch`() {
        val taps = AudioTapPluginSet()
        var calls = 0
        lateinit var plugin: AudioTapPlugin
        plugin = AudioTapPlugin { _, _, _, _ ->
            calls++
            taps.remove(plugin)
        }
        taps.add(plugin)
        taps.add(plugin)
        assertThat(taps.size).isEqualTo(1)
        taps.dispatch(toneBuffer(), PcmEncoding.PCM_FLOAT, 1, 48_000)
        taps.dispatch(toneBuffer(), PcmEncoding.PCM_FLOAT, 1, 48_000)
        assertThat(calls).isEqualTo(1)
    }
}
