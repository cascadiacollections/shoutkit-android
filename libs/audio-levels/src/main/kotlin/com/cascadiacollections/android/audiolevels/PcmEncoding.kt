package com.cascadiacollections.android.audiolevels

/**
 * The PCM sample formats [AudioSpectrumAnalyzer] can mix down. Interleaved, native byte order —
 * what Media3's `TeeAudioProcessor` and `AudioTrack` hand over.
 */
enum class PcmEncoding(internal val bytesPerSample: Int) {
    /** Signed 16-bit integers; `Short.MIN_VALUE` maps to −1. Media3's `C.ENCODING_PCM_16BIT`. */
    PCM_16BIT(bytesPerSample = 2),

    /** 32-bit floats in `−1..1`. Media3's `C.ENCODING_PCM_FLOAT`. */
    PCM_FLOAT(bytesPerSample = 4)
}
