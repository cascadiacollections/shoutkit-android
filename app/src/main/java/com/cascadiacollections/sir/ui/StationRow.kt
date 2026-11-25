package com.cascadiacollections.sir.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.core.model.Station

/**
 * A single station row, shared by the browse and library tabs.
 *
 * Tapping anywhere on the row starts playback, which leaves the trailing slot free for
 * the list-specific action (save, remove, ...). [onLongClick] is opt-in per call site
 * (the library's saved-stations section opens the edit sheet; browse results open the
 * automation menu). [subtitle] defaults to "Genre · 128 kbps", the same line the browse
 * results show; null or empty omits the supporting line. The station's name is the headline,
 * so the subtitle never repeats it.
 *
 * The artwork is the same tile the mini player and Now Playing use: the station's favicon,
 * or its monogram when there is none. The station that is selected keeps its artwork and is
 * marked by its name in the primary colour over "Now playing".
 */
@Composable
fun StationRow(
    station: Station,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    subtitle: String? = defaultSubtitle(station),
    trailing: @Composable (() -> Unit)? = null
) {
    ListItem(
        modifier = modifier.combinedClickable(
            enabled = station.isPlayable,
            onClickLabel = stringResource(R.string.play_station),
            onClick = onPlay,
            onLongClick = onLongClick
        ),
        headlineContent = {
            Text(
                text = station.name,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        },
        supportingContent = when {
            isPlaying -> {
                { Text(stringResource(R.string.station_now_playing)) }
            }

            subtitle.isNullOrEmpty() -> null

            else -> {
                { Text(subtitle) }
            }
        },
        leadingContent = {
            // Decorative: the row's click label already says "Play station".
            StationArtworkImage(
                station = station,
                stationName = station.name,
                size = ARTWORK_SIZE,
                modifier = Modifier.clip(RoundedCornerShape(ARTWORK_CORNER))
            )
        },
        trailingContent = trailing
    )
}

@Composable
@ReadOnlyComposable
private fun defaultSubtitle(station: Station): String {
    val resources = LocalResources.current
    return station.browseSubtitle { resources.getString(R.string.bitrate_kbps, it) }
}

private val ARTWORK_SIZE = 40.dp
private val ARTWORK_CORNER = 8.dp
