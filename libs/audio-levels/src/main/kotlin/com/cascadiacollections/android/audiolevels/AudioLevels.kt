package com.cascadiacollections.android.audiolevels

/**
 * One frame of a live level meter: a loudness value per frequency band, each normalized to
 * `0..1` (silence to full scale), lowest band first.
 *
 * It describes what is playing *right now*, not a waveform over time — the shape that fits
 * live audio, which has no duration to lay out and no position to scrub to.
 *
 * Immutable: the bands are copied in and never handed out as the backing array.
 */
class AudioLevels(bands: FloatArray) {
    private val values: FloatArray = bands.copyOf()

    constructor(bands: List<Float>) : this(bands.toFloatArray())

    /** Number of bands. */
    val size: Int get() = values.size

    /** The level of band [index], in `0..1`. */
    operator fun get(index: Int): Float = values[index]

    /** A copy of the bands. */
    fun toFloatArray(): FloatArray = values.copyOf()

    /** The bands as a list. */
    fun toList(): List<Float> = values.toList()

    /** Whether every band is zero — what a meter shows at rest. */
    val isSilent: Boolean get() = values.all { it == 0f }

    /**
     * These levels folded into [count] bands, each the loudest of the bands it covers. For a
     * compact meter (a mini player's handful of bars) that wants the same motion as the full
     * one rather than a second analysis.
     *
     * Returns `this` when [count] is not smaller than the current band count, and an empty
     * frame for `count <= 0`.
     */
    fun resampled(count: Int): AudioLevels = when {
        count <= 0 -> AudioLevels(FloatArray(0))

        count >= values.size -> this

        else -> AudioLevels(
            FloatArray(count) { group ->
                val lower = group * values.size / count
                val upper = maxOf(lower + 1, (group + 1) * values.size / count)
                var loudest = 0f
                for (band in lower until upper) loudest = maxOf(loudest, values[band])
                loudest
            }
        )
    }

    override fun equals(other: Any?): Boolean = other is AudioLevels && values.contentEquals(other.values)

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String = "AudioLevels(${values.contentToString()})"

    companion object {
        /**
         * The band count the spec's golden vectors use, and the default everywhere. Few enough
         * that each bar is wide enough to read on a phone, enough that the music's shape shows.
         */
        const val DEFAULT_BAND_COUNT = 24

        /** Every band at zero — what a meter shows at rest. */
        fun silent(bandCount: Int = DEFAULT_BAND_COUNT): AudioLevels = AudioLevels(FloatArray(bandCount))
    }
}
