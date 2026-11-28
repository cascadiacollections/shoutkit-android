package com.cascadiacollections.sir.core.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Holds the single live [DataStore] for [fileName], scoped to the current Application.
 *
 * DataStore requires that a file is owned by one instance at a time, which the
 * `preferencesDataStore` property delegate guarantees by caching one instance forever.
 * That delegate caches it against the *first* context it ever sees, though, which breaks
 * under Robolectric: a test class can get a fresh Application with a fresh files
 * directory, leaving the cached store writing through a directory that no longer exists.
 *
 * So the owning Application is tracked explicitly via a [WeakReference] identity check
 * (`owner?.get() === app`), and the target file is resolved before creating the store so
 * the `produceFile` lambda closes over a `File` rather than a `Context`. In production
 * the Application never changes. When a *different* Application appears, the previous
 * store's scope is cancelled before the replacement is built — DataStore only releases
 * its claim on the file when that scope completes, so without the cancel the second store
 * would either contend with the first or trip the "multiple DataStores active for the
 * same file" check.
 */
internal class AppScopedDataStore(private val fileName: String) {

    private var owner: WeakReference<Context>? = null
    private var ownerScope: CoroutineScope? = null
    private var instance: DataStore<Preferences>? = null

    @Synchronized
    operator fun get(context: Context): DataStore<Preferences> {
        val app = context.applicationContext
        instance?.let { existing -> if (owner?.get() === app) return existing }

        ownerScope?.cancel()

        val targetFile = app.preferencesDataStoreFile(fileName)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val created = PreferenceDataStoreFactory.create(scope = scope) { targetFile }
        owner = WeakReference(app)
        ownerScope = scope
        instance = created
        return created
    }
}
