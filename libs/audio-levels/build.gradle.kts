plugins {
    id("sir.android.lib")
}

// Pure Kotlin: the analysis has no Android or Media3 dependency, so any player that can hand
// over PCM (Media3's TeeAudioProcessor, AudioTrack, a test) can feed it. See README.md.
android {
    namespace = "com.cascadiacollections.android.audiolevels"
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
}
