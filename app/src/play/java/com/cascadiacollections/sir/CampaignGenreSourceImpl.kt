package com.cascadiacollections.sir

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Play build: read the Install Referrer once and keep only an allow-listed
 * `utm_content`. Click timestamps and the raw referrer string are not stored.
 * A missing Play Store, a service error, or a timeout yields null.
 */
class CampaignGenreSourceImpl(private val context: Context) : CampaignGenreSource {

    override suspend fun genreOrNull(): String? {
        val referrer = withTimeoutOrNull(REFERRER_TIMEOUT_MILLIS) { readReferrer() }
        return genreFromInstallReferrer(referrer)
    }

    private suspend fun readReferrer(): String? = suspendCancellableCoroutine { cont ->
        val client = runCatching { InstallReferrerClient.newBuilder(context).build() }.getOrNull()
        if (client == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { client.endConnection() } }
        val listener = object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(code: Int) {
                // Only the referrer string. Timestamps and the instant-experience flag
                // are not read, and the string itself is not stored.
                val referrer = if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                    runCatching { client.installReferrer.installReferrer }.getOrNull()
                } else {
                    null
                }
                runCatching { client.endConnection() }
                if (cont.isActive) cont.resume(referrer)
            }

            override fun onInstallReferrerServiceDisconnected() {
                if (cont.isActive) cont.resume(null)
            }
        }
        runCatching { client.startConnection(listener) }.onFailure {
            runCatching { client.endConnection() }
            if (cont.isActive) cont.resume(null)
        }
    }

    private companion object {
        const val REFERRER_TIMEOUT_MILLIS = 5_000L
    }
}
