package com.cascadiacollections.android.audiolevels

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Test

class AudioSpectrumAnalyzerTest {
    private fun sine(frames: Int, frequency: Double, amplitude: Double, sampleRate: Int) =
        FloatArray(frames) { (amplitude * sin(2 * PI * frequency * it / sampleRate)).toFloat() }

    private fun assertClose(actual: AudioLevels, expected: AudioLevels, tolerance: Float = 1e-4f) {
        assertThat(actual.size).isEqualTo(expected.size)
        for (band in 0 until expected.size) {
            assertThat(abs(actual[band] - expected[band]), "band $band").isLessThanOrEqualTo(tolerance)
        }
    }

    @Test
    fun `interleaved 16-bit, interleaved float and planar float agree`() {
        val left = sine(1024, 1000.0, 0.5, 48_000)
        val right = sine(1024, 3000.0, 0.25, 48_000)
        val planar = checkNotNull(AudioSpectrumAnalyzer().levels(listOf(left, right), 48_000))

        val floats = ByteBuffer.allocate(1024 * 2 * 4).order(ByteOrder.nativeOrder())
        val shorts = ByteBuffer.allocate(1024 * 2 * 2).order(ByteOrder.nativeOrder())
        for (frame in 0 until 1024) {
            floats.putFloat(left[frame]).putFloat(right[frame])
            shorts.putShort((left[frame] * 32_768).toInt().toShort())
            shorts.putShort((right[frame] * 32_768).toInt().toShort())
        }
        floats.flip()
        shorts.flip()

        val interleavedFloat = checkNotNull(AudioSpectrumAnalyzer().levels(floats, PcmEncoding.PCM_FLOAT, 2, 48_000))
        val interleavedShort = checkNotNull(AudioSpectrumAnalyzer().levels(shorts, PcmEncoding.PCM_16BIT, 2, 48_000))

        assertClose(interleavedFloat, planar)
        assertClose(interleavedShort, planar, tolerance = 0.005f)
        // The analyzer reads without moving the caller's buffer.
        assertThat(floats.position()).isEqualTo(0)
        assertThat(shorts.remaining()).isEqualTo(1024 * 2 * 2)
    }

    @Test
    fun `mixdown averages channels`() {
        val tone = sine(1024, 1000.0, 0.5, 48_000)
        val mono = checkNotNull(AudioSpectrumAnalyzer().levels(tone, 48_000))
        val stereo = checkNotNull(AudioSpectrumAnalyzer().levels(listOf(tone, tone.copyOf()), 48_000))
        assertClose(stereo, mono)
    }

    @Test
    fun `a full-scale sine reads near full scale before tilt`() {
        val analyzer = AudioSpectrumAnalyzer(tiltPerOctave = 0f)
        // Bin-centred so there is no scalloping loss: bin 64 of 1024 at 48 kHz.
        val levels = checkNotNull(analyzer.levels(sine(1024, 3000.0, 1.0, 48_000), 48_000))
        val loudest = (0 until levels.size).maxOf { levels[it] }
        assertThat(abs(loudest - 1f)).isLessThanOrEqualTo(0.01f)
    }

    @Test
    fun `rejects empty and invalid input`() {
        val analyzer = AudioSpectrumAnalyzer()
        assertThat(analyzer.levels(FloatArray(0), 44_100)).isNull()
        assertThat(analyzer.levels(FloatArray(10), 0)).isNull()
        assertThat(analyzer.levels(ByteBuffer.allocate(3), PcmEncoding.PCM_16BIT, 2, 44_100)).isNull()
        assertThat(analyzer.levels(ByteBuffer.allocate(8), PcmEncoding.PCM_FLOAT, 0, 44_100)).isNull()
    }

    @Test
    fun `every band has at least one bin and bands tile the spectrum`() {
        for (sampleRate in listOf(8_000f, 22_050f, 44_100f, 48_000f, 96_000f)) {
            val bins = AudioSpectrumAnalyzer.bandBins(24, 1024, sampleRate, 40f, 16_000f)
            assertThat(bins.size).isEqualTo(24)
            bins.forEach { assertThat(it.last - it.first + 1, "$sampleRate $it").isGreaterThan(0) }
            // Contiguous, except that bands above Nyquist all share its last bin (spec step 5).
            bins.zipWithNext().forEach { (lower, upper) ->
                assertThat(upper.first, "$sampleRate").isEqualTo(minOf(lower.last + 1, 511))
            }
            assertThat(bins.last().last).isLessThanOrEqualTo(511)
        }
    }

    @Test
    fun `fft matches a direct dft`() {
        val size = 64
        val input = DoubleArray(size) { sin(it * 0.37) + 0.25 * sin(it * 1.9) }
        val real = input.copyOf()
        val imaginary = DoubleArray(size)
        Fft(size).transform(real, imaginary)
        for (k in 0 until size) {
            var expectedReal = 0.0
            var expectedImaginary = 0.0
            for (n in 0 until size) {
                expectedReal += input[n] * kotlin.math.cos(2 * PI * k * n / size)
                expectedImaginary -= input[n] * sin(2 * PI * k * n / size)
            }
            assertThat(abs(real[k] - expectedReal)).isLessThanOrEqualTo(1e-9)
            assertThat(abs(imaginary[k] - expectedImaginary)).isLessThanOrEqualTo(1e-9)
        }
    }
}
