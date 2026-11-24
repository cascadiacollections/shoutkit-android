package com.cascadiacollections.sir.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.cascadiacollections.sir.FIRST_RUN_GENRES
import com.cascadiacollections.sir.core.directory.Tag
import com.cascadiacollections.sir.ui.theme.SirTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunWelcomeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `welcome names the app and dismisses on get started`() {
        var done = 0
        var picked: String? = "pending"
        composeRule.setContent {
            SirTheme {
                FirstRunWelcome(onDone = {
                    picked = it
                    done++
                })
            }
        }

        composeRule.onNodeWithText("Welcome to Shoutkit").assertIsDisplayed()
        composeRule.onNodeWithText("No accounts, no tracking. Find a station and press play.").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").performClick()

        assertThat(done).isEqualTo(1)
        assertThat(picked).isNull()
    }

    @Test
    fun `genre chips are shown and expose a selected state for TalkBack`() {
        composeRule.setContent {
            SirTheme { FirstRunWelcome(onDone = {}) }
        }

        composeRule.onNodeWithText("Start with a genre")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        FIRST_RUN_GENRES.forEach { genre ->
            composeRule.onNodeWithText(Tag(genre).displayName)
                .assertIsDisplayed()
                .assertIsNotSelected()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        }
    }

    @Test
    fun `picking a chip returns that genre`() {
        var picked: String? = null
        composeRule.setContent {
            SirTheme { FirstRunWelcome(onDone = { picked = it }) }
        }

        composeRule.onNodeWithText("Rock").performClick()
        composeRule.onNodeWithText("Rock").assertIsSelected()
        composeRule.onNodeWithText("Get started").performClick()

        assertThat(picked).isEqualTo("rock")
    }

    @Test
    fun `a preselected genre is selected and tapping it again clears the pick`() {
        var picked: String? = "pending"
        composeRule.setContent {
            SirTheme { FirstRunWelcome(preselected = "talk", onDone = { picked = it }) }
        }

        composeRule.onNodeWithText("Talk").assertIsSelected().performClick()
        composeRule.onNodeWithText("Talk").assertIsNotSelected()
        composeRule.onNodeWithText("Get started").performClick()

        assertThat(picked).isNull()
    }

    @Test
    fun `an unknown preselection selects nothing`() {
        var picked: String? = "pending"
        composeRule.setContent {
            SirTheme { FirstRunWelcome(preselected = "not-a-genre", onDone = { picked = it }) }
        }

        FIRST_RUN_GENRES.forEach { genre ->
            composeRule.onNodeWithText(Tag(genre).displayName).assertIsNotSelected()
        }
        composeRule.onNodeWithText("Get started").performClick()

        assertThat(picked).isNull()
    }
}
