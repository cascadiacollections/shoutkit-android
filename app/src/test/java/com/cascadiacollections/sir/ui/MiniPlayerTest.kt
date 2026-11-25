package com.cascadiacollections.sir.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.cascadiacollections.android.audiolevels.AudioLevels
import com.cascadiacollections.sir.RadioUiState
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.ui.theme.SirTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [MiniPlayer] with and without its five-bar level meter. The meter itself has no semantics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp")
class MiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val live = RadioUiState(
        isConnected = true,
        isPlaying = true,
        station = Station(id = "kexp", name = "KEXP", url = "https://k", tags = "indie")
    )

    @Test
    fun `the meter leaves the station name and the transport button in place`() {
        val frame = AudioLevels(FloatArray(AudioLevels.DEFAULT_BAND_COUNT) { it / 24f })
        composeRule.setContent {
            SirTheme {
                MiniPlayer(
                    state = live,
                    onToggle = {},
                    onClick = {},
                    showVisualizer = true,
                    audioLevels = flowOf(frame)
                )
            }
        }

        composeRule.onNodeWithText("KEXP").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `without the visualizer the row is unchanged`() {
        composeRule.setContent {
            SirTheme { MiniPlayer(state = live, onToggle = {}, onClick = {}) }
        }

        composeRule.onNodeWithText("KEXP").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed().assertIsEnabled()
    }
}
