package com.cascadiacollections.android.audiolevels

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An in-place iterative radix-2 complex FFT of a fixed power-of-two [size], forward direction
 * (`X[k] = Σ x[n]·e^(−2πikn/N)`). Twiddles and the bit-reversal table are computed once; each
 * [transform] allocates nothing. Not thread-safe only in that callers own the arrays they pass.
 */
internal class Fft(val size: Int) {
    private val cosTable: DoubleArray
    private val sinTable: DoubleArray
    private val reversed: IntArray

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two, was $size" }
        val half = size / 2
        cosTable = DoubleArray(half) { cos(2 * PI * it / size) }
        sinTable = DoubleArray(half) { -sin(2 * PI * it / size) }
        val bits = Integer.numberOfTrailingZeros(size)
        reversed = IntArray(size) { Integer.reverse(it) ushr (Int.SIZE_BITS - bits) }
    }

    /** Transforms ([real], [imaginary]) in place. Both must hold exactly [size] values. */
    fun transform(real: DoubleArray, imaginary: DoubleArray) {
        require(real.size == size && imaginary.size == size)
        for (index in 0 until size) {
            val target = reversed[index]
            if (target > index) {
                real.swap(index, target)
                imaginary.swap(index, target)
            }
        }
        var length = 2
        while (length <= size) {
            val halfLength = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                for (offset in 0 until halfLength) {
                    val twiddleReal = cosTable[offset * step]
                    val twiddleImaginary = sinTable[offset * step]
                    val even = start + offset
                    val odd = even + halfLength
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                }
                start += length
            }
            length *= 2
        }
    }

    private fun DoubleArray.swap(first: Int, second: Int) {
        val held = this[first]
        this[first] = this[second]
        this[second] = held
    }
}
