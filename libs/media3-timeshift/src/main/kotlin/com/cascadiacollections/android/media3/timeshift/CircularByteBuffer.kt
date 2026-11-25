package com.cascadiacollections.android.media3.timeshift

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe circular byte buffer for DVR-style time-shift buffering of live audio.
 *
 * A background producer thread writes stream bytes via [write], while a consumer
 * thread reads via [read]. The read cursor can be moved backward with
 * [seekBack] to replay older audio, and snapped to the write cursor with [goLive].
 */
class CircularByteBuffer(val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val data = ByteArray(capacity)
    private var writeOffset = 0L
    private var readOffset = 0L

    private val lock = ReentrantLock()
    private val dataAvailable = lock.newCondition()
    private var endOfStream = false

    /**
     * Bumped by [clear] and [resumeStream]. A [read] that was already waiting belongs to the
     * previous generation and must return [END_OF_STREAM] instead of parking again: the producer
     * that was going to write may be stalled or already gone.
     */
    private var generation = 0L

    /** [read] calls blocked in [dataAvailable]. Visible to tests so they can wait without sleeping. */
    private var waitingReaders = 0

    /**
     * Write bytes into the buffer from the producer thread.
     *
     * If the buffer is full and the write cursor overtakes the read cursor,
     * the read cursor is pushed forward (oldest unread data is discarded).
     *
     * @param src Source byte array to read from.
     * @param offset Starting offset in [src].
     * @param length Number of bytes to write. Values ≤ 0 are ignored.
     */
    fun write(src: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        lock.withLock {
            var srcOffset = offset
            var remaining = length
            // Only the last [capacity] bytes are retained. Skipping the prefix keeps
            // the absolute write cursor identical to copying one byte at a time.
            if (remaining > capacity) {
                val skip = remaining - capacity
                srcOffset += skip
                writeOffset += skip
                remaining = capacity
            }
            while (remaining > 0) {
                val writePos = indexFor(writeOffset)
                val spaceToEnd = capacity - writePos
                val chunk = remaining.coerceAtMost(spaceToEnd)
                System.arraycopy(src, srcOffset, data, writePos, chunk)
                writeOffset += chunk
                srcOffset += chunk
                remaining -= chunk
            }
            // The oldest retained offset only moves forward, so one check at the
            // end matches pushing the read cursor on every overwritten byte.
            val oldestOffset = oldestOffsetInternal()
            if (readOffset < oldestOffset) readOffset = oldestOffset
            dataAvailable.signalAll()
        }
    }

    /**
     * Read bytes from the buffer. Blocks while the buffer is empty and the producer
     * is still running.
     *
     * Uses [System.arraycopy] for bulk reads when possible.
     *
     * @param dst Destination byte array to write into.
     * @param offset Starting offset in [dst].
     * @param length Maximum number of bytes to read.
     * @return The number of bytes actually read, or [END_OF_STREAM] if the producer
     *   signalled [signalEndOfStream] with no buffered data left, if [clear] or [resumeStream]
     *   invalidated this call while it was waiting, or if the reading thread was interrupted.
     */
    fun read(dst: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        lock.withLock {
            val seen = generation
            while (availableInternal() == 0) {
                if (endOfStream || generation != seen) return END_OF_STREAM
                waitingReaders++
                try {
                    dataAvailable.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return END_OF_STREAM
                } finally {
                    waitingReaders--
                }
            }
            // [resumeStream] can invalidate a read that woke with bytes still buffered. Those
            // bytes belong to the producer that just took over, not to this call.
            if (generation != seen) return END_OF_STREAM
            val toRead = length.coerceAtMost(availableInternal())
            var remaining = toRead
            var dstPos = offset

            while (remaining > 0) {
                val readPos = indexFor(readOffset)
                val spaceToEnd = capacity - readPos
                val chunk = remaining.coerceAtMost(spaceToEnd)
                System.arraycopy(data, readPos, dst, dstPos, chunk)
                readOffset += chunk
                dstPos += chunk
                remaining -= chunk
            }
            return toRead
        }
    }

    /**
     * Move the read cursor backward by [bytes], allowing previously read data to be
     * re-read. The actual seek distance is clamped to the amount of data available
     * behind the read cursor.
     *
     * @param bytes Number of bytes to seek backward.
     */
    fun seekBack(bytes: Int) {
        lock.withLock {
            val maxSeekBack = seekBackAvailable()
            val actual = bytes.coerceAtMost(maxSeekBack)
            readOffset -= actual
        }
    }

    /** Snap the read cursor to the write cursor (resume live playback). */
    fun goLive() {
        lock.withLock {
            readOffset = writeOffset
        }
    }

    /** True when the read cursor is at the write cursor (no delay). */
    fun isLive(): Boolean = lock.withLock { readOffset == writeOffset }

    /** Number of bytes available to read (ahead of read cursor). */
    fun available(): Int = lock.withLock { availableInternal() }

    /**
     * Reset the buffer to its initial empty state, clearing any end-of-stream signal.
     *
     * A [read] blocked on an empty buffer returns [END_OF_STREAM] instead of staying parked.
     * [clear] itself does not leave the buffer ended: the next [read] waits for new data.
     * Signalling without retiring the generation would wake the reader, see an empty buffer
     * that is not ended, and park it again, so a stalled or already-finished producer would
     * pin the loader thread for good.
     */
    fun clear() {
        lock.withLock {
            writeOffset = 0L
            readOffset = 0L
            endOfStream = false
            retireWaitersLocked()
        }
    }

    /**
     * Signal that the producer has finished. Readers blocked in [read] wake up and,
     * once the remaining buffered data is drained, receive [END_OF_STREAM] instead of
     * blocking forever.
     *
     * Data already in the buffer stays readable, so [seekBack] can still replay it.
     */
    fun signalEndOfStream() {
        lock.withLock { endStreamLocked(generation) }
    }

    /**
     * [signalEndOfStream] for the producer that started at [epoch], as returned by [resumeStream].
     *
     * A producer that exits after a newer one has taken the buffer (its thread reaches `finally`
     * only once the socket actually closes) must not mark that newer stream ended.
     */
    internal fun signalEndOfStream(epoch: Long) {
        lock.withLock { endStreamLocked(epoch) }
    }

    /**
     * Clear a previous [signalEndOfStream] so a new producer can use the buffer, and release
     * any [read] still waiting on the old one.
     *
     * Returns the epoch the new producer must pass to [signalEndOfStream]. Buffered bytes stay
     * readable; only the in-flight read is retired, so it cannot consume the new producer's
     * bytes or stay blocked if that producer stalls.
     */
    fun resumeStream(): Long = lock.withLock {
        endOfStream = false
        retireWaitersLocked()
        generation
    }

    /** True while a [read] is blocked waiting for data or for the stream to end. */
    internal fun hasWaitingReader(): Boolean = lock.withLock { waitingReaders > 0 }

    /** Unlocked: retire in-flight reads and wake them. Call only while holding [lock]. */
    private fun retireWaitersLocked() {
        generation += 1
        dataAvailable.signalAll()
    }

    /** Unlocked. Call only while holding [lock]. */
    private fun endStreamLocked(epoch: Long) {
        if (epoch != generation) return
        endOfStream = true
        dataAvailable.signalAll()
    }

    /** True once [signalEndOfStream] has been called and not yet undone. */
    fun isEndOfStream(): Boolean = lock.withLock { endOfStream }

    /** True when at least [bytes] of previously read data can be replayed via [seekBack]. */
    fun canSeekBack(bytes: Int): Boolean = lock.withLock { seekBackAvailable() >= bytes }

    /**
     * Number of bytes behind the read cursor that can be seeked back to.
     * This is the data that has been read but is still in the buffer.
     */
    internal fun seekBackAvailable(): Int = lock.withLock {
        (readOffset - oldestOffsetInternal()).coerceAtLeast(0).toInt()
    }

    /** Unlocked available — call only while holding [lock]. */
    private fun availableInternal(): Int = (writeOffset - readOffset).coerceIn(0L, capacity.toLong()).toInt()

    private fun oldestOffsetInternal(): Long = (writeOffset - capacity).coerceAtLeast(0L)

    private fun indexFor(offset: Long): Int = (offset % capacity).toInt()

    companion object {
        /**
         * Returned by [read] when the stream has ended and the buffer is drained.
         * Matches Media3's `C.RESULT_END_OF_INPUT`.
         */
        const val END_OF_STREAM = -1
    }
}
