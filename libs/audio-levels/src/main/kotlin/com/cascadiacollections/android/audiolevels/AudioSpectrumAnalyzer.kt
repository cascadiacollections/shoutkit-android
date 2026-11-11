package com.cascadiacollections.android.audiolevels

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns PCM into per-band levels exactly as the audio levels spec (version 1) lays out: a mono
 * mixdown of the newest [frameSize] frames, a periodic Hann window, a DFT, the peak magnitude
 * in each log-spaced band, a per-octave tilt, and a decibel floor mapped to `0..1`.
 *
 * Spec: https://github.com/cascadiacollections/shoutkit/blob/main/docs/audio-levels-spec.md
 *
 * Not thread-safe: it reuses its scratch buffers between calls. [AudioLevelMeter] is the
 * thread-safe plug-in that wraps it.
 */
class AudioSpectrumAnalyzer(
    val frameSize: Int = FRAME_SIZE,
    val bandCount: Int = AudioLevels.DEFAULT_BAND_COUNT,
    private val minimumFrequency: Float = MINIMUM_FREQUENCY,
    private val maximumFrequency: Float = MAXIMUM_FREQUENCY,
    private val floorDecibels: Float = FLOOR_DECIBELS,
    tiltPerOctave: Float = TILT_PER_OCTAVE
) {
    init {
        require(bandCount > 0) { "bandCount must be positive" }
        require(minimumFrequency > 0 && maximumFrequency > minimumFrequency) { "invalid frequency range" }
        require(floorDecibels < 0) { "floorDecibels must be negative" }
    }

    private val fft = Fft(frameSize)
    private val window = DoubleArray(frameSize) { 0.5 - 0.5 * cos(2 * PI * it / frameSize) }
    private val bandTilts = FloatArray(bandCount) { band ->
        val center = minimumFrequency *
            (maximumFrequency / minimumFrequency).pow((band + 0.5f) / bandCount)
        tiltPerOctave * log2(center / TILT_PIVOT_FREQUENCY)
    }
    private val samples = FloatArray(frameSize)
    private val real = DoubleArray(frameSize)
    private val imaginary = DoubleArray(frameSize)
    private var bandBins: List<IntRange> = emptyList()
    private var bandBinsSampleRate = 0

    /**
     * Levels for the newest [frameSize] frames of interleaved [pcm] (from its position to its
     * limit, which are left untouched), mixed to mono and zero-padded at the end when shorter.
     * `null` when there is not one whole frame, or the format is invalid.
     */
    fun levels(pcm: ByteBuffer, encoding: PcmEncoding, channelCount: Int, sampleRate: Int): AudioLevels? {
        val bytesPerFrame = encoding.bytesPerSample * channelCount
        val frameCount = if (bytesPerFrame > 0) pcm.remaining() / bytesPerFrame else 0
        if (frameCount == 0 || sampleRate <= 0) return null
        val source = pcm.duplicate().order(ByteOrder.nativeOrder())
        val used = minOf(frameCount, frameSize)
        val firstByte = source.position() + (frameCount - used) * bytesPerFrame
        val scale = 1f / channelCount
        for (index in 0 until frameSize) {
            if (index >= used) {
                samples[index] = 0f
                continue
            }
            val frameStart = firstByte + index * bytesPerFrame
            var sum = 0f
            for (channel in 0 until channelCount) {
                val at = frameStart + channel * encoding.bytesPerSample
                sum += when (encoding) {
                    PcmEncoding.PCM_16BIT -> source.getShort(at) / SHORT_FULL_SCALE
                    PcmEncoding.PCM_FLOAT -> source.getFloat(at)
                }
            }
            samples[index] = sum * scale
        }
        return AudioLevels(bandLevels(sampleRate))
    }

    /**
     * Levels for planar float channels (`channels[c][frame]`, every channel the same length).
     * Gives the same result as the interleaved form. `null` for no channels or no frames.
     */
    fun levels(channels: List<FloatArray>, sampleRate: Int): AudioLevels? {
        val frameCount = channels.firstOrNull()?.size ?: 0
        if (frameCount == 0 || sampleRate <= 0 || channels.any { it.size != frameCount }) return null
        val used = minOf(frameCount, frameSize)
        val start = frameCount - used
        val scale = 1f / channels.size
        for (index in 0 until frameSize) {
            samples[index] = if (index < used) {
                var sum = 0f
                for (channel in channels) sum += channel[start + index]
                sum * scale
            } else {
                0f
            }
        }
        return AudioLevels(bandLevels(sampleRate))
    }

    /**
     * Levels for mono [samples] — the platform-neutral entry point the spec's golden vectors
     * exercise. `null` when [samples] is empty or [sampleRate] isn't positive.
     */
    fun levels(samples: FloatArray, sampleRate: Int): AudioLevels? = levels(listOf(samples), sampleRate)

    private fun bandLevels(sampleRate: Int): FloatArray {
        if (sampleRate != bandBinsSampleRate) {
            bandBins = bandBins(bandCount, frameSize, sampleRate.toFloat(), minimumFrequency, maximumFrequency)
            bandBinsSampleRate = sampleRate
        }
        for (index in 0 until frameSize) {
            real[index] = samples[index] * window[index]
            imaginary[index] = 0.0
        }
        fft.transform(real, imaginary)
        // A full-scale sine lands at magnitude N/2 in its bin, halved again by the Hann
        // window's coherent gain — so this maps it to ~1 (0 dBFS).
        val normalization = 4.0 / frameSize
        return FloatArray(bandCount) { band ->
            var peak = 0.0
            for (bin in bandBins[band]) {
                peak = maxOf(peak, sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]))
            }
            val decibels = 20 * log10(maxOf((peak * normalization).toFloat(), java.lang.Float.MIN_NORMAL))
            normalized(decibels + bandTilts[band])
        }
    }

    private fun normalized(decibels: Float): Float = ((decibels - floorDecibels) / -floorDecibels).coerceIn(0f, 1f)

    companion object {
        /** Spec constants. Changing any of these changes every golden vector. */
        const val FRAME_SIZE = 1024
        const val MINIMUM_FREQUENCY = 40f
        const val MAXIMUM_FREQUENCY = 16_000f
        const val FLOOR_DECIBELS = -60f

        /**
         * Music carries far more energy low than high — roughly pink, falling about 3 dB per
         * octave — so an untilted meter shows busy bass and a near-dead top half. Each band is
         * lifted this many dB per octave above [TILT_PIVOT_FREQUENCY] (and lowered below).
         */
        const val TILT_PER_OCTAVE = 3f
        const val TILT_PIVOT_FREQUENCY = 1_000f

        private const val SHORT_FULL_SCALE = 32_768f

        /**
         * DFT bin ranges (end-exclusive, as [IntRange]s) for [bandCount] log-spaced bands
         * between the two frequencies. Every band gets at least one bin, so low bands narrower
         * than a bin still move rather than sitting at zero forever.
         */
        internal fun bandBins(
            bandCount: Int,
            frameSize: Int,
            sampleRate: Float,
            minimumFrequency: Float,
            maximumFrequency: Float
        ): List<IntRange> {
            val nyquistBin = frameSize / 2
            val binWidth = sampleRate / frameSize
            val ratio = maximumFrequency / minimumFrequency
            fun binAtEdge(edge: Int): Int {
                val frequency = minimumFrequency * ratio.pow(edge.toFloat() / bandCount)
                // roundToInt rounds half up, which matches Swift's .rounded() for positive values.
                return (frequency / binWidth).roundToInt().coerceIn(1, nyquistBin)
            }
            var lower = binAtEdge(0)
            return List(bandCount) { band ->
                val start = minOf(lower, nyquistBin - 1)
                val end = minOf(nyquistBin, maxOf(binAtEdge(band + 1), start + 1))
                lower = end
                start until end
            }
        }
    }
}
