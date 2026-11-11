package com.cascadiacollections.android.audiolevels

/**
 * Display auto-gain for a level meter: scales each frame so its loudest band sits near full
 * height, against a reference that jumps up to any louder frame and decays slowly, never below
 * [MINIMUM_REFERENCE].
 *
 * Streams differ by 10 dB or more in mastering, so a fixed scale that suits a loud station draws
 * a quiet one as a row of dots. The floor keeps quiet passages and silence from being blown up
 * to full scale. Display only (spec, "Drawing (UI)"): the analysis and its golden vectors are
 * unaffected. Apply it before [AudioLevels.resampled] and before the display curve.
 *
 * Immutable: [apply] returns the scaled frame and the next state.
 */
class AutoGain private constructor(val reference: Float, private val lastUpdate: Long?) {
    constructor() : this(MINIMUM_REFERENCE, null)

    /** The result of [apply]: the scaled frame, and the gain to use for the next one. */
    data class Applied(val levels: AudioLevels, val gain: AutoGain)

    /** Advances the reference to [nowNanos] and scales [levels] by it, clamped to `0..1`. */
    fun apply(levels: AudioLevels, nowNanos: Long): Applied {
        val elapsedSeconds = lastUpdate?.let { (nowNanos - it) / NANOS_PER_SECOND } ?: 0f
        val loudest = (0 until levels.size).maxOfOrNull { levels[it] } ?: 0f
        val next = maxOf(MINIMUM_REFERENCE, loudest, reference - DECAY_RATE * elapsedSeconds)
        val scaled = AudioLevels(FloatArray(levels.size) { minOf(1f, levels[it] / next) })
        return Applied(scaled, AutoGain(next, nowNanos))
    }

    override fun equals(other: Any?): Boolean =
        other is AutoGain && reference == other.reference && lastUpdate == other.lastUpdate

    override fun hashCode(): Int = reference.hashCode()

    companion object {
        /** The quietest loudest-band level that is scaled to full height. */
        const val MINIMUM_REFERENCE = 0.3f

        /** How fast the reference falls back toward quieter material, in full scale per second. */
        const val DECAY_RATE = 0.15f

        private const val NANOS_PER_SECOND = 1e9f
    }
}
