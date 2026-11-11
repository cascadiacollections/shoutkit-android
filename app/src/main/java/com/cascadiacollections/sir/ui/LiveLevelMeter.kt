package com.cascadiacollections.sir.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cascadiacollections.android.audiolevels.AudioLevels
import com.cascadiacollections.android.audiolevels.PeakHold
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * The Now Playing level meter: mirrored bars that grow out from a centre line, with peak caps,
 * easing over [FRAME_EASE_MILLIS] between frames — the same drawing as the iOS
 * `AudioLevelsView(style: .mirrored)`.
 *
 * Collects [levels] only while [active] and the screen is at least started, so the player's tap
 * runs only while someone can see the bars. When playback pauses, buffers or stops (or frames
 * stop arriving), the bars ease to rest and the caps drop with them.
 *
 * Decorative: it has no semantics, since the status badge already says the stream is live.
 */
@Composable
internal fun LiveLevelMeter(active: Boolean, levels: Flow<AudioLevels>, modifier: Modifier = Modifier) {
    var target by remember { mutableStateOf(AudioLevels.silent()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(active, levels, lifecycleOwner) {
        target = AudioLevels.silent()
        if (!active) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            levels.collectLatest { frame ->
                target = frame
                // No frame for a while (Cast took over, an offloaded format): rest, don't freeze.
                delay(STALE_FRAME_MILLIS)
                target = AudioLevels.silent()
            }
        }
        target = AudioLevels.silent()
    }

    var from by remember { mutableStateOf(MeterFrame.REST) }
    var to by remember { mutableStateOf(MeterFrame.REST) }
    var peakHold by remember { mutableStateOf(PeakHold()) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        peakHold = peakHold.update(target, System.nanoTime())
        from = MeterFrame.lerp(from, to, progress.value)
        to = MeterFrame(target.toFloatArray(), FloatArray(peakHold.size) { peakHold[it] })
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = FRAME_EASE_MILLIS, easing = EaseOut))
    }

    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        drawMirroredBars(MeterFrame.lerp(from, to, progress.value), color)
    }
}

/** Whether the system's animator duration scale is 0 ("Remove animations"), read on resume. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reduceMotion by remember { mutableStateOf(false) }
    LaunchedEffect(context, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            reduceMotion = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }
    }
    return reduceMotion
}

/** Bar levels and peak caps, as drawn. */
private class MeterFrame(val levels: FloatArray, val peaks: FloatArray) {
    companion object {
        val REST = MeterFrame(FloatArray(AudioLevels.DEFAULT_BAND_COUNT), FloatArray(AudioLevels.DEFAULT_BAND_COUNT))

        fun lerp(start: MeterFrame, stop: MeterFrame, fraction: Float): MeterFrame {
            fun mix(a: FloatArray, b: FloatArray) = FloatArray(b.size) {
                val origin = a.getOrElse(it) { 0f }
                origin + (b[it] - origin) * fraction
            }
            return MeterFrame(mix(start.levels, stop.levels), mix(start.peaks, stop.peaks))
        }
    }
}

private fun DrawScope.drawMirroredBars(frame: MeterFrame, color: Color) {
    val count = frame.levels.size
    if (count == 0) return
    val spacing = BAR_SPACING.toPx()
    val minimumHeight = MINIMUM_BAR_HEIGHT.toPx()
    val capHeight = maxOf(MINIMUM_CAP_HEIGHT.toPx(), minimumHeight * CAP_TO_BAR_RATIO)
    val barWidth = ((size.width - spacing * (count - 1)) / count).coerceAtLeast(1f)
    val radius = CornerRadius(barWidth / 2, barWidth / 2)
    val centre = size.height / 2
    for (band in 0 until count) {
        val left = band * (barWidth + spacing)
        val barHeight = maxOf(minimumHeight, size.height * displayed(frame.levels[band]))
        drawRoundRect(color, Offset(left, centre - barHeight / 2), Size(barWidth, barHeight), radius)
        val peak = frame.peaks.getOrElse(band) { 0f }
        if (peak > 0f) {
            val offset = minOf(size.height - capHeight, size.height * displayed(peak)) / 2
            val capSize = Size(barWidth, capHeight)
            val capRadius = CornerRadius(capHeight / 2, capHeight / 2)
            for (y in floatArrayOf(centre - offset, centre + offset)) {
                drawRoundRect(color, Offset(left, y - capHeight / 2), capSize, capRadius, alpha = CAP_ALPHA)
            }
        }
    }
}

/**
 * Display curve from the spec's "Drawing (UI)" section: bars and caps at `level^0.6` of the
 * height. Typical stations sit at 0.1–0.4 per band, which drawn linearly reads as a dotted line.
 * Keeps 0 at 0 and 1 at 1; the analysis is unaffected.
 */
private fun displayed(level: Float): Float = level.coerceIn(0f, 1f).pow(DISPLAY_EXPONENT)

private const val DISPLAY_EXPONENT = 0.6f

/** Slightly longer than the ~33 ms publish interval, so steps read as motion (iOS: 0.12 s). */
private const val FRAME_EASE_MILLIS = 120
private const val STALE_FRAME_MILLIS = 500L
private val BAR_SPACING = 3.dp
private val MINIMUM_BAR_HEIGHT = 3.dp
private val MINIMUM_CAP_HEIGHT = 1.5.dp
private const val CAP_TO_BAR_RATIO = 0.6f
private const val CAP_ALPHA = 0.55f
