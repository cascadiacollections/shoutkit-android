package com.cascadiacollections.sir

import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * The [MediaLibrarySession.Callback] that [RadioPlaybackService] hands Media3: who may
 * connect (the service is exported for Android Auto, so [ControllerPolicy] is the only
 * gate), which commands a live stream offers, the custom commands, and the Auto browse
 * entry points. [ControllerPolicyTest] covers the policy itself; this covers its wiring.
 *
 * The callback is anonymous, so it is read off the live session. Only browse paths that
 * stay off the network are exercised here (Top Stations and search hit the directory).
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioPlaybackServiceSessionCallbackTest {

    private lateinit var serviceController: ServiceController<RadioPlaybackService>
    private lateinit var session: MediaLibrarySession
    private lateinit var callback: MediaLibrarySession.Callback

    @Before
    fun setUp() {
        serviceController = Robolectric.buildService(RadioPlaybackService::class.java).create()
        val service = serviceController.get()
        session = checkNotNull(service.onGetSession(controller(service.packageName)))
        callback = sessionCallback(session)
    }

    @After
    fun tearDown() {
        serviceController.destroy()
    }

    // --- onConnect: who may bind ---

    @Test
    fun `own app is accepted`() {
        assertThat(connect(controller(serviceController.get().packageName)).isAccepted).isTrue()
    }

    @Test
    fun `unknown untrusted app is rejected`() {
        assertThat(connect(controller("com.example.snoop")).isAccepted).isFalse()
    }

    @Test
    fun `app spoofing our package prefix is rejected`() {
        val prefixed = serviceController.get().packageName + ".evil"
        assertThat(connect(controller(prefixed)).isAccepted).isFalse()
    }

    @Test
    fun `android auto is accepted`() {
        assertThat(connect(controller("com.google.android.projection.gearhead")).isAccepted).isTrue()
    }

    @Test
    fun `trusted controller is accepted`() {
        assertThat(connect(controller("com.example.launcher", trusted = true)).isAccepted).isTrue()
    }

    @Test
    fun `accepted controller gets live-radio player commands only`() {
        val commands = connect(controller("com.google.android.projection.gearhead")).availablePlayerCommands
        assertThat(commands.contains(Player.COMMAND_PLAY_PAUSE)).isTrue()
        assertThat(commands.contains(Player.COMMAND_STOP)).isTrue()
        listOf(
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ).forEach { assertThat(commands.contains(it), "command $it").isFalse() }
    }

    @Test
    fun `accepted controller gets the app's custom session commands`() {
        val commands = connect(controller("com.google.android.projection.gearhead")).availableSessionCommands
        listOf(
            RadioPlaybackService.ACTION_SEEK_BACK,
            RadioPlaybackService.ACTION_GO_LIVE,
            RadioPlaybackService.ACTION_TOGGLE_FAVORITE
        ).forEach { assertThat(commands.contains(SessionCommand(it, Bundle.EMPTY)), it).isTrue() }
    }

    // --- onCustomCommand ---

    @Test
    fun `seek back is not supported while the feature is off`() {
        assertThat(RadioPlaybackService.SEEKBACK_ENABLED).isFalse()
        assertThat(customCommand(RadioPlaybackService.ACTION_SEEK_BACK).resultCode)
            .isEqualTo(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
    }

    @Test
    fun `go live is not supported while the feature is off`() {
        assertThat(customCommand(RadioPlaybackService.ACTION_GO_LIVE).resultCode)
            .isEqualTo(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
    }

    @Test
    fun `toggle favorite with no station selected is not supported`() {
        // A fresh install plays the default SIR stream, which cannot be saved.
        assertThat(customCommand(RadioPlaybackService.ACTION_TOGGLE_FAVORITE).resultCode)
            .isEqualTo(SessionError.ERROR_NOT_SUPPORTED)
    }

    @Test
    fun `unknown custom command is not handled`() {
        assertThat(customCommand("com.example.UNKNOWN").resultCode)
            .isEqualTo(SessionError.ERROR_NOT_SUPPORTED)
    }

    // --- Android Auto browsing ---

    @Test
    fun `library root advertises search and content styles`() {
        val result = callback.onGetLibraryRoot(session, auto, null).await()
        assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS)
        assertThat(result.value?.mediaId).isEqualTo(AutoBrowseTree.ROOT_ID)
        val extras = checkNotNull(result.params).extras
        assertThat(extras.getBoolean(AutoLibrary.EXTRAS_KEY_SEARCH_SUPPORTED)).isTrue()
        assertThat(extras.getInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE))
            .isEqualTo(MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
    }

    @Test
    fun `library root carries the caller's offline, recent and suggested flags`() {
        val params = LibraryParams.Builder().setOffline(true).setRecent(true).setSuggested(true).build()
        val result = callback.onGetLibraryRoot(session, auto, params).await()
        val rootParams = checkNotNull(result.params)
        assertThat(rootParams.isOffline).isTrue()
        assertThat(rootParams.isRecent).isTrue()
        assertThat(rootParams.isSuggested).isTrue()
    }

    @Test
    fun `root children honour the limit announced at getLibraryRoot`() {
        val unlimited = children(AutoBrowseTree.ROOT_ID).value.orEmpty().size
        val hints = Bundle().apply { putInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 1) }
        callback.onGetLibraryRoot(session, auto, LibraryParams.Builder().setExtras(hints).build()).await()

        val limited = children(AutoBrowseTree.ROOT_ID)
        assertThat(limited.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS)
        assertThat(limited.value?.size).isEqualTo(1)
        assertThat(unlimited > 1, "unlimited root has $unlimited children").isTrue()
    }

    @Test
    fun `root children limit is forgotten on disconnect`() {
        val hints = Bundle().apply { putInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 1) }
        callback.onGetLibraryRoot(session, auto, LibraryParams.Builder().setExtras(hints).build()).await()
        callback.onDisconnected(session, auto)
        assertThat(children(AutoBrowseTree.ROOT_ID).value.orEmpty().size > 1).isTrue()
    }

    @Test
    fun `your stations starts with the SIR stream`() {
        val result = children(AutoBrowseTree.Category.YOUR_STATIONS.id)
        assertThat(result.resultCode).isEqualTo(LibraryResult.RESULT_SUCCESS)
        assertThat(result.value.orEmpty()).isNotEmpty()
        assertThat(result.value!!.first().mediaId).isEqualTo(AutoBrowseTree.SIR_STREAM_ID)
    }

    @Test
    fun `children of an unknown node is a bad value`() {
        assertThat(children("not-a-node").resultCode).isEqualTo(SessionError.ERROR_BAD_VALUE)
    }

    @Test
    fun `getItem resolves the root and the SIR stream`() {
        assertThat(callback.onGetItem(session, auto, AutoBrowseTree.ROOT_ID).await().value?.mediaId)
            .isEqualTo(AutoBrowseTree.ROOT_ID)
        assertThat(callback.onGetItem(session, auto, AutoBrowseTree.SIR_STREAM_ID).await().value?.mediaId)
            .isEqualTo(AutoBrowseTree.SIR_STREAM_ID)
    }

    @Test
    fun `getItem for a category returns it`() {
        val id = AutoBrowseTree.Category.RECENTLY_PLAYED.id
        assertThat(callback.onGetItem(session, auto, id).await().value?.mediaId).isEqualTo(id)
    }

    @Test
    fun `adding the SIR stream item plays the default stream`() {
        val items = callback.onAddMediaItems(
            session,
            auto,
            mutableListOf(androidx.media3.common.MediaItem.Builder().setMediaId(AutoBrowseTree.SIR_STREAM_ID).build())
        ).await()
        assertThat(items.size).isEqualTo(1)
        assertThat(items.single().localConfiguration).isNotNull()
    }

    // --- helpers ---

    private val auto: MediaSession.ControllerInfo by lazy { controller("com.google.android.projection.gearhead") }

    private fun connect(controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
        callback.onConnect(session, controller)

    private fun customCommand(action: String): SessionResult =
        callback.onCustomCommand(session, auto, SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY).await()

    private fun children(parentId: String) = callback.onGetChildren(session, auto, parentId, 0, 100, null).await()

    // Arguments: package, pid, uid, library version, interface version, trusted,
    // connection hints, package name verified.
    private fun controller(packageName: String, trusted: Boolean = false): MediaSession.ControllerInfo =
        MediaSession.ControllerInfo.createTestOnlyControllerInfo(packageName, 0, 0, 0, 0, trusted, Bundle.EMPTY, true)

    /** Runs the main looper until [this] completes; the service's scope is Dispatchers.Main. */
    private fun <T> ListenableFuture<T>.await(): T {
        repeat(AWAIT_ATTEMPTS) {
            shadowOf(Looper.getMainLooper()).idle()
            if (isDone) return get()
            Thread.sleep(AWAIT_STEP_MS)
        }
        error("future did not complete")
    }

    private companion object {
        const val AWAIT_ATTEMPTS = 500
        const val AWAIT_STEP_MS = 10L

        /** The anonymous callback the service built the session with. */
        fun sessionCallback(session: MediaSession): MediaLibrarySession.Callback {
            val impl = MediaSession::class.java.getDeclaredMethod("getImpl")
                .apply { isAccessible = true }
                .invoke(session)
            var type: Class<*>? = impl.javaClass
            while (type != null) {
                val field = type.declaredFields.firstOrNull { it.name == "callback" }
                if (field != null) {
                    field.isAccessible = true
                    return field.get(impl) as MediaLibrarySession.Callback
                }
                type = type.superclass
            }
            error("MediaSessionImpl.callback not found; Media3 internals changed")
        }
    }
}
