package com.cascadiacollections.android.media3.timeshift

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import org.junit.Test

@UnstableApi
class TimeShiftDataSourceTest {

    /** Serves [bytes] once, reporting [length] from open (the HTTP Content-Length, or unset). */
    private class FakeUpstream(private val bytes: ByteArray, private val length: Long) : DataSource {
        private var position = 0
        var closed = false

        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = length
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == bytes.size) return C.RESULT_END_OF_INPUT
            val count = length.coerceAtMost(bytes.size - position)
            System.arraycopy(bytes, position, buffer, offset, count)
            position += count
            return count
        }

        override fun getUri(): Uri? = null
        override fun close() {
            closed = true
        }
    }

    private val spec = DataSpec(mockk<Uri>(relaxed = true))
    private val payload = byteArrayOf(1, 2, 3, 4, 5)

    private fun readAll(source: DataSource): List<Byte> {
        val out = mutableListOf<Byte>()
        val chunk = ByteArray(2)
        while (true) {
            val read = source.read(chunk, 0, chunk.size)
            if (read == C.RESULT_END_OF_INPUT) return out
            repeat(read) { out += chunk[it] }
        }
    }

    @Test
    fun `a finite resource passes through with its length, bypassing the buffer`() {
        val controller = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)
        val upstream = FakeUpstream(payload, length = payload.size.toLong())
        val source = TimeShiftDataSource(upstream, controller)

        // The length reaching the player is what gives a newscast file a duration, and so
        // tells its end apart from a live stream dropping.
        assertThat(source.open(spec)).isEqualTo(payload.size.toLong())
        assertThat(readAll(source)).isEqualTo(payload.toList())
        assertThat(controller.buffer.available()).isEqualTo(0)

        source.close()
        assertThat(upstream.closed).isTrue()
    }

    @Test
    fun `a stream of unknown length reads through the time-shift buffer`() {
        val controller = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)
        val source = TimeShiftDataSource(FakeUpstream(payload, length = C.LENGTH_UNSET.toLong()), controller)

        assertThat(source.open(spec)).isEqualTo(C.LENGTH_UNSET.toLong())
        // The reader thread fills the buffer, then marks the end so this drains and stops.
        assertThat(readAll(source)).isEqualTo(payload.toList())
        source.close()
    }

    @Test
    fun `a live source opened after a finite one buffers again`() {
        val controller = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)
        val finite = TimeShiftDataSource(FakeUpstream(payload, length = payload.size.toLong()), controller)
        finite.open(spec)
        readAll(finite)
        finite.close()

        val live = TimeShiftDataSource(FakeUpstream(byteArrayOf(9, 8), length = C.LENGTH_UNSET.toLong()), controller)
        assertThat(live.open(spec)).isEqualTo(C.LENGTH_UNSET.toLong())
        assertThat(readAll(live)).isEqualTo(listOf<Byte>(9, 8))
        live.close()
    }

    @Test
    fun `close unblocks a reader while the upstream stays stalled`() {
        val controller = TimeShiftController(capacityBytes = 1024, bytesPerSecond = 8)
        val upstream = StuckUpstream()
        val source = TimeShiftDataSource(upstream, controller)
        assertThat(source.open(spec)).isEqualTo(C.LENGTH_UNSET.toLong())

        val result = AtomicInteger(Int.MIN_VALUE)
        val finished = CountDownLatch(1)
        thread(isDaemon = true) {
            result.set(source.read(ByteArray(16), 0, 16))
            finished.countDown()
        }
        assertThat(upstream.enteredRead.await(2, TimeUnit.SECONDS)).isTrue()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!controller.buffer.hasWaitingReader()) {
            if (System.nanoTime() >= deadline) throw AssertionError("reader did not block")
            Thread.yield()
        }

        try {
            source.close()
            assertThat(
                finished.await(2, TimeUnit.SECONDS),
                name = "loader stayed blocked after close"
            ).isTrue()
            assertThat(result.get()).isEqualTo(C.RESULT_END_OF_INPUT)
        } finally {
            upstream.release()
        }
    }

    /**
     * An upstream read that does not return when interrupted or closed, the way a socket
     * read behaves. Only an explicit [release] lets the producer thread exit, so a passing
     * test cannot be explained by the producer's own end-of-stream signal.
     */
    private class StuckUpstream : DataSource {
        private val lock = ReentrantLock()
        private val releasedCondition = lock.newCondition()
        val enteredRead = CountDownLatch(1)

        @Volatile
        private var released = false

        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = C.LENGTH_UNSET.toLong()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            enteredRead.countDown()
            lock.withLock {
                while (!released) {
                    try {
                        releasedCondition.await()
                    } catch (_: InterruptedException) {
                        // Ignored, as a blocking socket read ignores the loader interrupt.
                    }
                }
            }
            return C.RESULT_END_OF_INPUT
        }

        override fun getUri(): Uri? = null
        override fun close() = Unit

        fun release() {
            lock.withLock {
                released = true
                releasedCondition.signalAll()
            }
        }
    }
}
