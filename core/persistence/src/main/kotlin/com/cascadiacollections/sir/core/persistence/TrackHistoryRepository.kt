package com.cascadiacollections.sir.core.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kept apart from the settings file: up to [HeardTracks.LIMIT] entries is a JSON blob a
 * couple of orders of magnitude larger than every setting combined, and DataStore rewrites
 * the whole file on each edit — so sharing a file would make every settings toggle pay
 * for the history, and every track change rewrite the settings.
 */
private val trackHistoryDataStore = AppScopedDataStore("track_history")

/**
 * Persisted "Recently Heard" track history, newest first.
 *
 * Written by the playback service, where ICY metadata is resolved, so history accrues
 * whether or not any UI is alive; read by the player's history sheet and the Library.
 */
class TrackHistoryRepository(context: Context) {

    private val dataStore = trackHistoryDataStore[context]
    private val tracksKey = stringPreferencesKey("tracks")

    /** Every retained track, newest first. */
    val tracks: Flow<List<HeardTrack>> = dataStore.data.map { preferences ->
        HeardTracks.decode(preferences[tracksKey])
    }

    /** Records [track], merging a consecutive repeat, in one transaction. */
    suspend fun record(track: HeardTrack) {
        dataStore.edit { preferences ->
            val current = HeardTracks.decode(preferences[tracksKey])
            val updated = HeardTracks.record(current, track)
            if (updated != current) preferences[tracksKey] = HeardTracks.encode(updated)
        }
    }

    /** Adds cover art to the current hearing; see [HeardTracks.attachArtwork]. */
    suspend fun attachArtwork(title: String, artist: String?, stationId: String?, artworkUrl: String) {
        dataStore.edit { preferences ->
            val current = HeardTracks.decode(preferences[tracksKey])
            val updated = HeardTracks.attachArtwork(current, title, artist, stationId, artworkUrl)
            if (updated != current) preferences[tracksKey] = HeardTracks.encode(updated)
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(tracksKey) }
    }
}
