# audio-levels

A live audio level meter for Android: PCM in, 24 log-spaced band levels out, ready to draw as
bars. MIT licensed (see [`LICENSE`](LICENSE)). Pure Kotlin: no Media3 or Android framework
dependency, only `kotlinx-coroutines-core` for the `Flow`.

It implements the [audio levels spec](https://github.com/cascadiacollections/shoutkit/blob/main/docs/audio-levels-spec.md)
(version 1), the same one as the iOS client's MIT
[`AudioLevels`](https://github.com/cascadiacollections/shoutkit/tree/main/Packages/AudioLevels)
Swift package, and checks the spec's shared golden vectors
([`src/test/resources/golden-vectors.json`](src/test/resources/golden-vectors.json)), so both
clients draw the same bars from the same audio.

## Components

| Type | What it does |
|---|---|
| `AudioSpectrumAnalyzer` | Mono mixdown (16-bit or float, interleaved or planar), periodic Hann window, its own 1024-point radix-2 FFT, 24 log bands 40 Hz–16 kHz, +3 dB/octave tilt, −60 dB floor |
| `AudioLevels` | One frame of band levels in `0..1`; `resampled(count)` folds them for compact meters |
| `AudioLevelMeter` | An `AudioTapPlugin` that adds ballistics (attack 0.7, release 0.18) and a 33 ms publish cap |
| `AudioTapPlugin` / `AudioTapPluginSet` | What an engine hands PCM to, and the thread-safe set it dispatches through |
| `AudioTapPluginSet.levels()` | A cold `Flow<AudioLevels>`: a meter is added while collected, removed when collection stops |
| `PeakHold` | Time-based peak caps: hold 600 ms, then fall at 0.9 full scale per second |

## Usage with Media3

```kotlin
val taps = AudioTapPluginSet()

val renderersFactory = object : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, float: Boolean, params: Boolean): AudioSink =
        DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(float)
            .setEnableAudioOutputPlaybackParameters(params)
            .setAudioProcessors(arrayOf(TeeAudioProcessor(sinkDispatchingTo(taps))))
            .build()
}
val player = ExoPlayer.Builder(context, renderersFactory).build()

// In the UI, only while the meter is on screen:
taps.levels().collect { frame -> draw(frame) }
```

The sink maps `C.ENCODING_PCM_16BIT` / `C.ENCODING_PCM_FLOAT` from `flush()` to `PcmEncoding`
and calls `taps.dispatch(...)` from `handleBuffer()`. ShoutKit's version is
`app/src/main/java/com/cascadiacollections/sir/LiveAudioLevels.kt`.

## When to meter

Per the spec: tap only while something is consuming levels, hide the meter when the system asks
for reduced motion (animator duration scale 0) or the user turns it off, and don't stop it for
battery saver — it is about thirty 1024-point FFTs a second at most.

## Changing the spec

Any constant change changes the golden vectors. Bump the spec version, regenerate the vectors in
the iOS repo, copy `golden-vectors.json` here, and ship both clients together.
