package com.cascadiacollections.android.audiolevels

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The spec's shared golden vectors (golden-vectors.json, copied from the iOS repo's
 * Packages/AudioLevels). Both clients pass the same file, so both draw the same bars.
 */
class GoldenVectorTest {
    private val golden: JsonObject = Json.parseToJsonElement(
        checkNotNull(javaClass.getResource("/golden-vectors.json")).readText()
    ).jsonObject
    private val tolerance = golden.getValue("tolerance").jsonPrimitive.float

    @Test
    fun `every vector matches within tolerance`() {
        val vectors = golden.getValue("vectors").jsonArray.map { it.jsonObject }
        assertThat(vectors.size).isEqualTo(EXPECTED_VECTOR_COUNT)
        for (vector in vectors) {
            val name = vector.getValue("name").jsonPrimitive.content
            val sampleRate = vector.getValue("sampleRate").jsonPrimitive.int
            val expected = vector.getValue("expected").jsonArray.map { it.jsonPrimitive.float }
            val samples = samples(vector, sampleRate)

            val actual = checkNotNull(AudioSpectrumAnalyzer().levels(samples, sampleRate)) { name }

            assertThat(actual.size, name).isEqualTo(expected.size)
            expected.forEachIndexed { band, value ->
                assertThat(abs(actual[band] - value), "$name band $band: ${actual[band]} vs $value")
                    .isLessThanOrEqualTo(tolerance)
            }
        }
    }

    @Test
    fun `smoothing matches the ballistics vector`() {
        val smoothing = golden.getValue("smoothing").jsonObject
        val inputs = smoothing.getValue("inputs").jsonArray.map { it.jsonPrimitive.float }
        val expected = smoothing.getValue("expected").jsonArray.map { it.jsonPrimitive.float }
        var previous = floatArrayOf(0f)
        inputs.forEachIndexed { step, input ->
            previous = AudioLevelMeter.smooth(previous, floatArrayOf(input))
            assertThat(abs(previous[0] - expected[step]), "step $step").isLessThanOrEqualTo(SMOOTHING_TOLERANCE)
        }
    }

    /** The spec's input: tones in double precision, plus LCG noise, stored as float. */
    private fun samples(vector: JsonObject, sampleRate: Int): FloatArray {
        val frames = vector.getValue("frames").jsonPrimitive.int
        val noise = vector.getValue("noiseAmplitude").jsonPrimitive.double
        val tones = vector.getValue("tones").jsonArray.map {
            val tone = it.jsonObject
            tone.getValue("frequency").jsonPrimitive.double to tone.getValue("amplitude").jsonPrimitive.double
        }
        var state = 1L
        return FloatArray(frames) { frame ->
            var value = tones.sumOf { (frequency, amplitude) ->
                amplitude * sin(2 * PI * frequency * frame / sampleRate)
            }
            if (noise > 0) {
                state = (LCG_MULTIPLIER * state + LCG_INCREMENT) and UINT32_MASK
                value += noise * (state.toDouble() / TWO_TO_THE_31 - 1)
            }
            value.toFloat()
        }
    }

    private companion object {
        const val EXPECTED_VECTOR_COUNT = 8
        const val SMOOTHING_TOLERANCE = 1e-4f
        const val LCG_MULTIPLIER = 1_664_525L
        const val LCG_INCREMENT = 1_013_904_223L
        const val UINT32_MASK = 0xFFFF_FFFFL
        const val TWO_TO_THE_31 = 2_147_483_648.0
    }
}
