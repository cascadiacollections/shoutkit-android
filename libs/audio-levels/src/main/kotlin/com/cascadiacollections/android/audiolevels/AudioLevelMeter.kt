package com.cascadiacollections.android.audiolevels

import java.nio.ByteBuffer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * The level meter as an [AudioTapPlugin]: an [AudioSpectrumAnalyzer] plus what a meter needs
 * on top of raw levels — a cap on how often it publishes, and ballistics (fast rise, slow
 * fall) so bars read as motion, not flicker. Each published frame goes to [onFrame], on the
 * audio thread.
 *
 * Most callers want [AudioTapPluginSet.levels] instead, which adds a meter only while a
 * collector is listening.
 *
 * Thread-safe: every call runs under one lock.
 */
class AudioLevelMeter(
    bandCount: Int = AudioLevels.DEFAULT_BAND_COUNT,
    private val minimumIntervalNanos: Long = DEFAULT_MINIMUM_INTERVAL_NANOS,
    private val nanoTime: () -> Long = System::nanoTime,
    private val onFrame: (AudioLevels) -> Unit
) : AudioTapPlugin {
    private val lock = Any()
    private val analyzer = AudioSpectrumAnalyzer(bandCount = bandCount)
    private var smoothed = FloatArray(bandCount)
    private var lastPublished: Long? = null

    override fun process(pcm: ByteBuffer, encoding: PcmEncoding, channelCount: Int, sampleRate: Int) {
        val frame = synchronized(lock) {
            val now = nanoTime()
            val last = lastPublished
            if (last != null && now - last < minimumIntervalNanos) return
            val raw = analyzer.levels(pcm, encoding, channelCount, sampleRate) ?: return
            smoothed = smooth(smoothed, raw.toFloatArray())
            lastPublished = now
            AudioLevels(smoothed)
        }
        onFrame(frame)
    }

    companion object {
        /** Spec ballistics: how much of the gap to a louder reading closes per frame. */
        const val ATTACK = 0.7f

        /** How much of the gap to a quieter reading closes per frame. */
        const val RELEASE = 0.18f

        /** The default publishing cap, 33 ms: 30 frames a second is as fast as bars need. */
        const val DEFAULT_MINIMUM_INTERVAL_NANOS = 33_000_000L

        /** One step of the spec's ballistics, band by band. */
        fun smooth(previous: FloatArray, next: FloatArray): FloatArray = FloatArray(minOf(previous.size, next.size)) {
            val old = previous[it]
            val new = next[it]
            old + (new - old) * if (new > old) ATTACK else RELEASE
        }
    }
}

/**
 * Live levels from this set's engine. Each collector gets its own [AudioLevelMeter], added to
 * the set when collection starts and removed when it stops — so the analysis runs only while
 * something is drawing. Keeps only the newest unread frame, like the Swift meter's stream.
 */
fun AudioTapPluginSet.levels(
    bandCount: Int = AudioLevels.DEFAULT_BAND_COUNT,
    minimumIntervalNanos: Long = AudioLevelMeter.DEFAULT_MINIMUM_INTERVAL_NANOS
): Flow<AudioLevels> = callbackFlow {
    val meter = AudioLevelMeter(bandCount, minimumIntervalNanos) { trySend(it) }
    add(meter)
    awaitClose { remove(meter) }
}.buffer(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
