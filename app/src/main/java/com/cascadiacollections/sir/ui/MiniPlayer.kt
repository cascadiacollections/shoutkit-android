@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cascadiacollections.sir.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cascadiacollections.android.audiolevels.AudioLevels
import com.cascadiacollections.sir.PlaybackStatus
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.displayedFailure
import com.cascadiacollections.sir.status
import com.cascadiacollections.sir.trackLine
import com.cascadiacollections.sir.transportAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
/**
 * Compact now-playing bar shown above the navigation bar on every tab except listen,
 * so playback stays reachable while browsing.
 *
 * Always mounted at a fixed height (rather than conditionally composed) so switching tabs
 * never shifts the navigation chrome above it. Until the controller connects the row is an
 * idle placeholder; interactivity is gated on that, not on whether a track is known — a
 * stream can be actively playing with no ICY title.
 *
 * Shows the station's artwork (or the track's album art) and name over the track line, or
 * the short form of a failure. Tapping the bar returns to the listen tab; only the
 * transport button is a separate target, which keeps the touch target for "go back to what
 * I'm hearing" large.
 */
@Composable
fun MiniPlayer(
    state: RadioUiState,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showVisualizer: Boolean = false,
    audioLevels: Flow<AudioLevels> = emptyFlow()
) {
    val isIdle = !state.isConnected
    val stationName = state.station?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.station_name)
    val failure = state.displayedFailure
    val subtitle = when (state.status) {
        PlaybackStatus.FAILED -> failure?.let { stringResource(it.displayMessageRes(short = true)) }
        PlaybackStatus.RECONNECTING -> stringResource(R.string.stream_reconnecting)
        else -> null
    } ?: state.trackLine() ?: stringResource(R.string.track_live_radio)
    val action = state.transportAction

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column {
            if (state.isBuffering) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick, enabled = !isIdle)
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NowPlayingArtwork(
                    albumArtUrl = state.albumArtUrl,
                    station = state.station,
                    stationName = stationName,
                    size = 40.dp,
                    shape = RoundedCornerShape(8.dp)
                )
                // One accessibility element: "<station>, <track or status>".
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Text(
                        text = stationName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIdle) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.status == PlaybackStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Five bars folded from the same 24-band stream Now Playing draws, as in the
                // iOS mini player (spec, "Compact meters"). Hidden by the Settings switch or
                // when the system removes animations.
                if (showVisualizer && !rememberReduceMotion()) {
                    LiveLevelMeter(
                        active = state.status == PlaybackStatus.LIVE,
                        levels = audioLevels,
                        modifier = Modifier.size(width = 22.dp, height = 16.dp),
                        displayBandCount = MINI_METER_BARS,
                        showPeaks = false,
                        barSpacing = 2.dp,
                        minimumBarHeight = 2.dp
                    )
                }
                IconButton(onClick = onToggle, enabled = !isIdle) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = stringResource(action.labelRes)
                    )
                }
            }
        }
    }
}

/** The mini player's bar count, as on iOS. */
private const val MINI_METER_BARS = 5
