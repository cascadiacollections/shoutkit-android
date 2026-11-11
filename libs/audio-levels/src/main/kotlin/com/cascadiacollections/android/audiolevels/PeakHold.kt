package com.cascadiacollections.android.audiolevels

/**
 * Peak caps for a level meter: each band's cap jumps to any louder level, holds there for
 * [HOLD_DURATION_NANOS], then falls at [FALL_RATE] until a level catches it. Time-based rather
 * than frame-based, so caps behave the same whether frames arrive ten or thirty times a second.
 *
 * Immutable: [update] returns the next state.
 */
class PeakHold private constructor(
    private val values: FloatArray,
    private val heldSince: LongArray,
    private val lastUpdate: Long?
) {
    constructor(bandCount: Int = AudioLevels.DEFAULT_BAND_COUNT) :
        this(FloatArray(bandCount), LongArray(bandCount) { NOT_HELD }, null)

    /** The caps, one per band. */
    val peaks: List<Float> get() = values.toList()

    /** The cap of band [index]. */
    operator fun get(index: Int): Float = values[index]

    val size: Int get() = values.size

    /**
     * The caps advanced to [nowNanos] against [levels]. A change in band count starts over, and
     * a silent frame ("at rest") resets them, so caps drop with the bars on pause.
     */
    fun update(levels: AudioLevels, nowNanos: Long): PeakHold {
        if (levels.isSilent) return PeakHold(levels.size)
        val base = if (levels.size == values.size) this else PeakHold(levels.size)
        val peaks = base.values.copyOf()
        val held = base.heldSince.copyOf()
        val elapsedSeconds = base.lastUpdate?.let { (nowNanos - it) / NANOS_PER_SECOND } ?: 0f
        for (band in 0 until levels.size) {
            val level = levels[band]
            val holding = held[band] != NOT_HELD && nowNanos - held[band] < HOLD_DURATION_NANOS
            when {
                level >= peaks[band] -> {
                    peaks[band] = level
                    held[band] = nowNanos
                }

                holding -> Unit

                else -> {
                    held[band] = NOT_HELD
                    peaks[band] = maxOf(level, peaks[band] - FALL_RATE * elapsedSeconds)
                }
            }
        }
        return PeakHold(peaks, held, nowNanos)
    }

    override fun equals(other: Any?): Boolean = other is PeakHold &&
        values.contentEquals(other.values) &&
        heldSince.contentEquals(other.heldSince) &&
        lastUpdate == other.lastUpdate

    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        /** How long a cap holds before falling: 600 ms. */
        const val HOLD_DURATION_NANOS = 600_000_000L

        /** How fast a cap falls, in full scale per second. */
        const val FALL_RATE = 0.9f

        private const val NOT_HELD = Long.MIN_VALUE
        private const val NANOS_PER_SECOND = 1e9f
    }
}
