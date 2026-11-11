package com.cascadiacollections.sir

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.cascadiacollections.android.audiolevels.AudioLevels
import com.cascadiacollections.android.audiolevels.AudioTapPluginSet
import com.cascadiacollections.android.audiolevels.PcmEncoding
import com.cascadiacollections.android.audiolevels.levels
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.Flow

/**
 * The bridge from [RadioPlaybackService]'s audio pipeline to the Now Playing level meter.
 * The service and the UI share a process, so one tap set is enough: the player's
 * [TeeAudioProcessor] dispatches decoded PCM into [taps], and each collector of [levels] adds a
 * meter there for as long as it collects (see "Live level meter" in docs/architecture.md).
 *
 * With nothing collecting, the tee's only cost is checking that the set is empty.
 */
object LiveAudioLevels {
    val taps = AudioTapPluginSet()

    /** Live meter frames, 24 bands per the shared spec. Cold: taps only while collected. */
    val levels: Flow<AudioLevels> = taps.levels()

    /** A renderers factory whose audio sink tees decoded PCM into [taps]. */
    @OptIn(UnstableApi::class)
    fun renderersFactory(context: Context): DefaultRenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .setAudioProcessors(arrayOf(TeeAudioProcessor(TapSink(taps))))
            .build()
    }

    /**
     * Hands each decoded buffer to the tap set. Media3 calls both methods on its playback
     * thread, [flush] whenever the format is (re)configured.
     */
    @OptIn(UnstableApi::class)
    private class TapSink(private val taps: AudioTapPluginSet) : TeeAudioProcessor.AudioBufferSink {
        private var encoding: PcmEncoding? = null
        private var channelCount = 0
        private var sampleRate = 0

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            this.sampleRate = sampleRateHz
            this.channelCount = channelCount
            this.encoding = when (encoding) {
                C.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16BIT

                C.ENCODING_PCM_FLOAT -> PcmEncoding.PCM_FLOAT

                // Other PCM layouts don't reach this tee from AAC/MP3 decoders; skip rather than guess.
                else -> null
            }
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val encoding = encoding ?: return
            if (taps.isEmpty) return
            taps.dispatch(buffer, encoding, channelCount, sampleRate)
        }
    }
}
