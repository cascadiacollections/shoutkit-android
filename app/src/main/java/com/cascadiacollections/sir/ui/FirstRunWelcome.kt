package com.cascadiacollections.sir.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cascadiacollections.sir.FIRST_RUN_GENRES
import com.cascadiacollections.sir.R
import com.cascadiacollections.sir.allowListedGenre
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import com.cascadiacollections.sir.firstRunPreselection
import com.cascadiacollections.sir.ui.icons.Radio
import kotlinx.coroutines.launch

/**
 * One-time welcome shown over the shell on first launch (ShoutKit's first-run sheet).
 *
 * Genre chips are optional. [preselected] is a saved campaign genre or the seasonal
 * default; leaving every chip off keeps today's behavior of opening on Listen.
 * Dismissing any way — the button, back, or a tap outside — counts as done, so the
 * welcome can never trap someone, nor a macrobenchmark that taps through cold starts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstRunWelcome(onDone: (picked: String?) -> Unit, modifier: Modifier = Modifier, preselected: String? = null) {
    var selected by rememberSaveable { mutableStateOf(allowListedGenre(preselected)) }
    var touched by rememberSaveable { mutableStateOf(false) }
    // A campaign genre can arrive after the first frame (DataStore, or a later link).
    // Until the person taps a chip, follow that preset.
    LaunchedEffect(preselected) {
        if (!touched) selected = allowListedGenre(preselected)
    }
    val finish = {
        onDone(allowListedGenre(selected))
    }
    AlertDialog(
        onDismissRequest = finish,
        modifier = modifier,
        icon = { Icon(Icons.Filled.Radio, contentDescription = null) },
        title = { Text(stringResource(R.string.welcome_title, stringResource(R.string.app_name))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.welcome_message))
                Text(
                    text = stringResource(R.string.welcome_genre_prompt),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FIRST_RUN_GENRES.forEach { genre ->
                        WelcomeGenreChip(
                            genre = genre,
                            selected = selected == genre,
                            onClick = {
                                touched = true
                                selected = if (selected == genre) null else genre
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = finish) {
                Text(stringResource(R.string.welcome_get_started))
            }
        }
    )
}

/**
 * Shows the welcome until the first run is dismissed.
 *
 * [onChooseGenre] runs only when a chip is selected. No chip means the caller leaves
 * the shell exactly as it was.
 */
@Composable
internal fun FirstRunWelcomeHost(
    repository: SettingsRepository,
    onChooseGenre: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val completed by repository.hasCompletedFirstRun.collectAsState<Boolean, Boolean?>(initial = null)
    val savedGenre by repository.firstRunGenre.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    if (completed == false) {
        FirstRunWelcome(
            preselected = firstRunPreselection(savedGenre),
            onDone = { picked ->
                scope.launch { repository.setHasCompletedFirstRun(true) }
                allowListedGenre(picked)?.let(onChooseGenre)
            },
            modifier = modifier
        )
    }
}

@Composable
private fun WelcomeGenreChip(genre: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(Tag(genre).displayName) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        }
    )
}
