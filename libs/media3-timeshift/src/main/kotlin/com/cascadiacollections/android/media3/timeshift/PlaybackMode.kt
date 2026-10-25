package com.cascadiacollections.android.media3.timeshift

/**
 * Playback mode for time-shifted live streams.
 *
 * Deliberately closed: a stream is either at the live edge or behind it, so callers may
 * `when` over this type without an `else` branch. That makes adding a subtype a breaking
 * change for every consumer (a caller compiled against the old set throws
 * `NoWhenBranchMatchedException` at runtime), so it requires a major version bump.
 */
sealed interface PlaybackMode {
    data object Live : PlaybackMode
    data object TimeShifted : PlaybackMode
}
