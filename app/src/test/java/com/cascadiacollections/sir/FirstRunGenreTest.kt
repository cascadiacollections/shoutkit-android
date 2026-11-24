package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.util.Calendar
import org.junit.Test

class FirstRunGenreTest {

    @Test
    fun `christmas season runs from november 15 through december 31`() {
        assertThat(seasonalFallback(month = 11, dayOfMonth = 14)).isNull()
        assertThat(seasonalFallback(month = 11, dayOfMonth = 15)).isEqualTo("christmas")
        assertThat(seasonalFallback(month = 11, dayOfMonth = 30)).isEqualTo("christmas")
        assertThat(seasonalFallback(month = 12, dayOfMonth = 1)).isEqualTo("christmas")
        assertThat(seasonalFallback(month = 12, dayOfMonth = 31)).isEqualTo("christmas")
        assertThat(seasonalFallback(month = 1, dayOfMonth = 1)).isNull()
        assertThat(seasonalFallback(month = 10, dayOfMonth = 31)).isNull()
        assertThat(seasonalFallback(month = 6, dayOfMonth = 15)).isNull()
    }

    @Test
    fun `calendar overload reads that calendar's month and day`() {
        assertThat(seasonalFallback(date(2026, Calendar.NOVEMBER, 14))).isNull()
        assertThat(seasonalFallback(date(2026, Calendar.NOVEMBER, 15))).isEqualTo("christmas")
        assertThat(seasonalFallback(date(2024, Calendar.DECEMBER, 31))).isEqualTo("christmas")
        assertThat(seasonalFallback(date(2026, Calendar.JANUARY, 1))).isNull()
    }

    @Test
    fun `only allow-listed genres are accepted`() {
        assertThat(allowListedGenre("Rock")).isEqualTo("rock")
        assertThat(allowListedGenre(" christmas ")).isEqualTo("christmas")
        assertThat(allowListedGenre("JAZZ")).isEqualTo("jazz")
        assertThat(allowListedGenre("pop")).isNull()
        assertThat(allowListedGenre(null)).isNull()
        assertThat(allowListedGenre("")).isNull()
        assertThat(allowListedGenre(" ")).isNull()
        assertThat(allowListedGenre("x".repeat(MAX_GENRE_TOKEN_LENGTH + 1))).isNull()
        assertThat(FIRST_RUN_GENRES).isEqualTo(
            setOf("christmas", "talk", "rock", "news", "jazz", "country")
        )
    }

    @Test
    fun `preselection prefers a saved genre, then the season, then nothing`() {
        val christmasEve = date(2026, Calendar.DECEMBER, 24)
        val summer = date(2026, Calendar.JULY, 1)

        assertThat(firstRunPreselection("rock", christmasEve)).isEqualTo("rock")
        assertThat(firstRunPreselection(" ROCK ", christmasEve)).isEqualTo("rock")
        assertThat(firstRunPreselection(null, christmasEve)).isEqualTo("christmas")
        assertThat(firstRunPreselection("nope", christmasEve)).isEqualTo("christmas")
        assertThat(firstRunPreselection(null, summer)).isNull()
        assertThat(firstRunPreselection("nope", summer)).isNull()
        assertThat(firstRunPreselection("x".repeat(80), christmasEve)).isEqualTo("christmas")
    }

    private fun date(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
}
