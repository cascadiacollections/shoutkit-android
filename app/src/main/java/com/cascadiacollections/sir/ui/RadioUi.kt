@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.cascadiacollections.sir.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.net.toUri
import com.cascadiacollections.android.audiolevels.AudioLevels
import com.cascadiacollections.sir.PlaybackStatus
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.canFavorite
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.playback.StreamFailure
import com.cascadiacollections.sir.displayedFailure
import com.cascadiacollections.sir.status
import com.cascadiacollections.sir.trackLine
import com.cascadiacollections.sir.transportAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The full-screen listen surface, including its own app bar.
 *
 * Kept as the entry point used by previews and screenshot tests; the tabbed shell
 * renders [ListenScreen] directly instead, because the shell owns the scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioUi(
    modifier: Modifier,
    isConnected: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isError: Boolean = false,
    trackTitle: String? = null,
    artist: String? = null,
    sleepTimerLabel: String? = null,
    showSettingsButton: Boolean = false,
    station: Station? = null,
    failure: StreamFailure? = null,
    onSettingsClick: () -> Unit = {},
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Scaffold(
        modifier = modifier,
        topBar = {
            if (showSettingsButton) {
                TopAppBar(
                    title = {},
                    actions = {
                        if (isPlaying && trackTitle != null) {
                            IconButton(onClick = {
                                val shareText = listOfNotNull(trackTitle, artist).joinToString(" — ")
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                resources.getString(R.string.share_now_playing, shareText)
                                            )
                                        },
                                        null
                                    )
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        ListenScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = RadioUiState(
                isConnected = isConnected,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isError = isError,
                trackTitle = trackTitle,
                artist = artist,
                sleepTimerLabel = sleepTimerLabel,
                station = station,
                failure = failure
            ),
            onToggle = onToggle
        )
    }
}

/**
 * The listen surface (ShoutKit's Now Playing) without any scaffolding, so it can be dropped
 * into the tabbed shell (which owns the app bar, mini player and navigation bar) as well as
 * into [RadioUi].
 *
 * Station artwork (or the track's album art) over the station name and "Title — Artist",
 * a status badge, the typed failure in ShoutKit's words, and the transport row: favourite,
 * a play button whose action follows the state (Play / Pause / Cancel / Retry), and an
 * overflow holding the one external link, "Open in Apple Music".
 */
@Composable
fun ListenScreen(
    state: RadioUiState,
    modifier: Modifier = Modifier,
    isWideLayout: Boolean = false,
    showVisualizer: Boolean = true,
    audioLevels: Flow<AudioLevels> = emptyFlow(),
    onToggleFavorite: () -> Unit = {},
    onToggle: () -> Unit
) {
    // Widen margins on medium/expanded screens (Pixel 10 Pro Fold, tablets) so the
    // centered content doesn't span the full display width. isWideLayout is derived once
    // from WindowSizeClass at the shell level (SirAppShell) rather than recomputed here.
    val horizontalPadding = if (isWideLayout) 72.dp else 24.dp
    val maxArtworkSize = if (isWideLayout) 280.dp else 240.dp

    val stationName = state.station?.name?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.station_name)
    val trackLine = state.trackLine() ?: stringResource(R.string.track_live_radio)
    val status = state.status
    val failure = state.displayedFailure

    Column(modifier = modifier) {
        if (state.isBuffering) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Measured before the scroll container (inside it the height is unbounded), so the
        // layout can follow the window: a short, wide one (a phone held sideways) puts the
        // artwork beside the controls, and a short, narrow one (split screen) shrinks the
        // artwork. Either way the transport controls stay on screen.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val windowHeight = maxHeight
            val windowWidth = maxWidth
            val sideBySide = windowHeight < SIDE_BY_SIDE_MAX_HEIGHT && windowWidth >= SIDE_BY_SIDE_MIN_WIDTH
            val artwork: @Composable (Dp) -> Unit = { size ->
                NowPlayingArtwork(
                    albumArtUrl = state.albumArtUrl,
                    station = state.station,
                    stationName = stationName,
                    size = size,
                    shape = RoundedCornerShape(28.dp)
                )
            }
            val details: @Composable (Dp) -> Unit = { spacing ->
                NowPlayingDetails(
                    stationName = stationName,
                    trackLine = trackLine,
                    status = status,
                    failure = failure,
                    spacing = spacing
                )
            }
            val transport: @Composable () -> Unit = {
                TransportRow(state = state, onToggleFavorite = onToggleFavorite, onToggle = onToggle)
            }
            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        // At least the window's height, so the row is centred in it.
                        .heightIn(min = windowHeight)
                        .padding(horizontal = horizontalPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
                ) {
                    val heightLimit = windowHeight - SIDE_BY_SIDE_ARTWORK_INSET
                    val widthLimit = windowWidth * SIDE_BY_SIDE_ARTWORK_WIDTH_FRACTION
                    artwork(min(maxArtworkSize, min(heightLimit, widthLimit)))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = SIDE_BY_SIDE_CONTROLS_MAX_WIDTH),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        details(8.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        NowPlayingMeter(
                            visible = showVisualizer,
                            active = status == PlaybackStatus.LIVE,
                            levels = audioLevels,
                            height = 32.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        transport()
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        artwork(min(maxArtworkSize, windowHeight * ARTWORK_HEIGHT_FRACTION))
                        Spacer(modifier = Modifier.height(20.dp))
                        details(12.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        NowPlayingMeter(
                            visible = showVisualizer,
                            active = status == PlaybackStatus.LIVE,
                            levels = audioLevels
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        transport()
                    }
                }
            }
        }
    }
}

/**
 * The status badge over the station name and track line, then the typed failure's long text
 * when playback has failed. [spacing] is the gap under the badge and above the failure text.
 */
@Composable
private fun NowPlayingDetails(
    stationName: String,
    trackLine: String,
    status: PlaybackStatus,
    failure: StreamFailure?,
    spacing: Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StatusBadge(status = status, failure = failure)
        // One accessibility element: "<station>, <track>".
        Column(
            modifier = Modifier
                .padding(top = spacing)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stationName,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trackLine,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (failure != null && status == PlaybackStatus.FAILED) {
            Text(
                text = stringResource(failure.displayMessageRes(short = false)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = spacing)
            )
        }
    }
}

/** Favourite, the play button (Play / Pause / Cancel / Retry) and the overflow; the sleep timer under them. */
@Composable
private fun TransportRow(state: RadioUiState, onToggleFavorite: () -> Unit, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val action = state.transportAction
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FavoriteButton(
                isFavorite = state.isFavorite,
                enabled = state.canFavorite,
                onToggle = onToggleFavorite
            )
            LargeFloatingActionButton(
                onClick = onToggle,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = stringResource(action.labelRes),
                    modifier = Modifier.size(36.dp)
                )
            }
            NowPlayingOverflow(trackViewUrl = state.trackViewUrl)
        }
        state.sleepTimerLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The live level meter, or a spacer of the same height when it is turned off in Settings or the
 * system removes animations, so the transport row stays where it was.
 */
@Composable
private fun NowPlayingMeter(visible: Boolean, active: Boolean, levels: Flow<AudioLevels>, height: Dp = 48.dp) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(height)
    if (visible && !rememberReduceMotion()) {
        LiveLevelMeter(active = active, levels = levels, modifier = modifier)
    } else {
        Spacer(modifier = modifier)
    }
}

/** Stacked layout: at most this share of the window height goes to the artwork. */
private const val ARTWORK_HEIGHT_FRACTION = 0.4f

/**
 * Below this height the stacked column cannot fit even with its smallest artwork, so a window
 * that is also at least [SIDE_BY_SIDE_MIN_WIDTH] wide puts the artwork beside the controls.
 */
private val SIDE_BY_SIDE_MAX_HEIGHT = 480.dp

/** The narrowest window with room for the artwork and the transport row side by side. */
private val SIDE_BY_SIDE_MIN_WIDTH = 480.dp

/** Side by side: the artwork leaves this much of the window height free... */
private val SIDE_BY_SIDE_ARTWORK_INSET = 32.dp

/** ...and takes at most this share of the window width. */
private const val SIDE_BY_SIDE_ARTWORK_WIDTH_FRACTION = 0.35f

/** Side by side: the controls column stops growing here, so the level meter stays a meter. */
private val SIDE_BY_SIDE_CONTROLS_MAX_WIDTH = 360.dp

/** Connecting… / Reconnecting… / Live / the failure's short text. Nothing while idle. */
@Composable
private fun StatusBadge(status: PlaybackStatus, failure: StreamFailure?) {
    val text = when (status) {
        PlaybackStatus.CONNECTING -> stringResource(R.string.status_connecting)
        PlaybackStatus.RECONNECTING -> stringResource(R.string.stream_reconnecting)
        PlaybackStatus.LIVE -> stringResource(R.string.status_live)
        PlaybackStatus.FAILED -> failure?.let { stringResource(it.displayMessageRes(short = true)) }
        PlaybackStatus.IDLE -> null
    } ?: return
    val (container, content) = when (status) {
        PlaybackStatus.FAILED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer

        PlaybackStatus.LIVE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer

        else ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        // Announce state changes (connecting → live, live → error) without a focus move.
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** The favourite toggle for the current station; disabled for the app's own stream. */
@Composable
private fun FavoriteButton(isFavorite: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (isFavorite) R.string.favorite_on else R.string.favorite_off)
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        modifier = Modifier.semantics { stateDescription = stateText }
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(R.string.favorite),
            tint = if (isFavorite && enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * The overflow menu. Its only entry is "Open in Apple Music" — ShoutKit's one external link —
 * so the button is only shown when the album-art lookup found the track's page. An empty
 * slot of the same size keeps the play button centred either way.
 */
@Composable
private fun NowPlayingOverflow(trackViewUrl: String?) {
    if (trackViewUrl == null) {
        Spacer(modifier = Modifier.size(48.dp))
        return
    }
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.open_in_apple_music)) },
                onClick = {
                    expanded = false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, trackViewUrl.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        // No browser or Music app to handle it; nothing useful to do.
                    }
                }
            )
        }
    }
}
