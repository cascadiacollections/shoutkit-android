package com.cascadiacollections.sir.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ListenScreen] in a phone held sideways. The sizes are the listen pane of a Pixel 10 in
 * landscape (923 x 411 dp window, less the navigation rail and the top bar), where the
 * stacked layout used to leave the play button below the fold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w923dp-h411dp-land")
class ListenScreenLandscapeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `transport controls are on screen without scrolling`() {
        setListenScreen(RadioUiState(isConnected = true))

        composeRule.onNodeWithContentDescription("Play").assertFullyVisible()
        composeRule.onNodeWithContentDescription("Favorite").assertFullyVisible()
        composeRule.onNodeWithText("Shoutkit").assertFullyVisible()
        composeRule.onNodeWithText("Live radio").assertFullyVisible()
    }

    @Test
    fun `live badge still leaves the controls on screen`() {
        setListenScreen(
            RadioUiState(
                isConnected = true,
                isPlaying = true,
                station = Station(id = "kexp", name = "KEXP", url = "https://k", tags = "indie")
            )
        )

        composeRule.onNodeWithText("Live").assertFullyVisible()
        composeRule.onNodeWithText("KEXP").assertFullyVisible()
        composeRule.onNodeWithContentDescription("Pause").assertFullyVisible()
    }

    private fun setListenScreen(state: RadioUiState) {
        composeRule.setContent {
            SirTheme {
                Box(modifier = Modifier.size(width = 777.dp, height = 299.dp)) {
                    ListenScreen(state = state, isWideLayout = true, onToggle = {})
                }
            }
        }
    }

    /** The node exists and none of it is clipped by the scroll container or the window. */
    private fun SemanticsNodeInteraction.assertFullyVisible() {
        val node = fetchSemanticsNode()
        val visible = node.boundsInRoot
        assertThat(visible.width.toInt() to visible.height.toInt())
            .isEqualTo(node.size.width to node.size.height)
    }
}
