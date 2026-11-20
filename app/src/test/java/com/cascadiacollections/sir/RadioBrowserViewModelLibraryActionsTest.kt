package com.cascadiacollections.sir

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.cascadiacollections.sir.core.directory.RadioDirectory
import com.cascadiacollections.sir.core.model.Station
import com.cascadiacollections.sir.core.model.StationQuery
import com.cascadiacollections.sir.core.persistence.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The library actions behind Browse and Library (save, unsave, reorder, play, recents),
 * driven through [RadioBrowserViewModel] against a real [SettingsRepository] so each one
 * is checked by what the UI then observes, not by which repository method was called.
 *
 * Writes go through `viewModelScope.launch` to DataStore on a background thread, so each
 * assertion polls [RadioBrowserViewModel.uiState] (as [RadioBrowserViewModelPlaylistTest]
 * does) instead of assuming the write has landed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioBrowserViewModelLibraryActionsTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule()

    private val repo by lazy { SettingsRepository(RuntimeEnvironment.getApplication()) }

    private val a = station("a")
    private val b = station("b")
    private val c = station("c")

    @Before
    fun reset() = runBlocking {
        repo.savedStations.first().forEach { repo.removeStation(it.id) }
        repo.clearRecentStations()
        repo.clearSelectedStation()
    }

    private fun viewModel() = RadioBrowserViewModel(EmptyDirectory, repo).also(coroutineRule::registerViewModel)

    @Test
    fun `save then remove updates saved stations and isStationSaved`() {
        val vm = viewModel()
        vm.saveStation(a)
        vm.saveStation(b)
        vm.awaitState("a and b saved") { s -> s.savedStations.map { it.id } == listOf("a", "b") }
        assertThat(vm.isStationSaved(a)).isTrue()

        vm.removeStation("a")
        vm.awaitState("a removed") { s -> s.savedStations.map { it.id } == listOf("b") }
        assertThat(vm.isStationSaved(a)).isFalse()
        assertThat(vm.isStationSaved(b)).isTrue()
    }

    @Test
    fun `move and reorder change the saved order`() {
        val vm = viewModel()
        listOf(a, b, c).forEach(vm::saveStation)
        vm.awaitState("three saved") { it.savedStations.size == 3 }

        vm.moveSavedStation(from = 0, to = 2)
        vm.awaitState("a moved to the end") { s -> s.savedStations.map { it.id } == listOf("b", "c", "a") }

        vm.reorderSavedStations(listOf("c", "a", "b"))
        vm.awaitState("dragged order persisted") { s -> s.savedStations.map { it.id } == listOf("c", "a", "b") }
    }

    @Test
    fun `playing a station selects it and records it as recent`() {
        val vm = viewModel()
        vm.playStation(a)
        vm.awaitState("a selected") { it.selectedStationId == "a" }
        vm.awaitState("a recent") { s -> s.recentStations.map { it.id } == listOf("a") }
    }

    @Test
    fun `an unplayable station is not selected`() {
        val vm = viewModel()
        vm.playStation(Station(id = "no-url", name = "No URL"))
        vm.playStation(a)
        vm.awaitState("a selected") { it.selectedStationId == "a" }
        assertThat(vm.uiState.value.recentStations.map { it.id }).containsExactly("a")
    }

    @Test
    fun `playDefaultStream clears the selection`() {
        val vm = viewModel()
        vm.playStation(a)
        vm.awaitState("a selected") { it.selectedStationId == "a" }
        vm.playDefaultStream()
        vm.awaitState("selection cleared") { it.selectedStationId == null }
        assertThat(vm.uiState.value.selectedStationId).isNull()
    }

    @Test
    fun `recents can be removed one at a time or cleared`() {
        val vm = viewModel()
        listOf(a, b, c).forEach(vm::playStation)
        vm.awaitState("three recents") { it.recentStations.size == 3 }

        vm.removeRecentStation("b")
        vm.awaitState("b removed") { s -> s.recentStations.none { it.id == "b" } }
        assertThat(vm.uiState.value.recentStations.size).isEqualTo(2)

        vm.clearRecentStations()
        vm.awaitState("recents cleared") { it.recentStations.isEmpty() }
        assertThat(vm.uiState.value.recentStations).isEmpty()
    }

    @Test
    fun `exported backup lists saved stations in order`() {
        val vm = viewModel()
        listOf(b, a).forEach(vm::saveStation)
        vm.awaitState("two saved") { it.savedStations.size == 2 }
        val backup = vm.exportFavoritesBackup()
        assertThat(backup.indexOf("https://example.com/b")).isLessThan(backup.indexOf("https://example.com/a"))
    }

    @Test
    fun `factory builds the view model`() {
        val vm = RadioBrowserViewModel.Factory(EmptyDirectory, repo).create(RadioBrowserViewModel::class.java)
        coroutineRule.registerViewModel(vm)
        assertThat(vm.uiState.value.savedStations).isEmpty()
    }

    private fun RadioBrowserViewModel.awaitState(what: String, predicate: (RadioBrowserUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5)
        while (!predicate(uiState.value)) {
            assertThat(System.currentTimeMillis(), name = "timed out waiting for: $what").isLessThan(deadline)
            Thread.sleep(10)
        }
    }

    private fun station(id: String) = Station(id = id, name = "Station $id", url = "https://example.com/$id")

    private object EmptyDirectory : RadioDirectory {
        override suspend fun search(query: StationQuery) = Result.success(emptyList<Station>())
        override suspend fun topStations(limit: Int) = Result.success(emptyList<Station>())
        override suspend fun stationsByTag(tag: String, limit: Int) = Result.success(emptyList<Station>())
        override suspend fun getStation(id: String) = Result.success<Station?>(null)
    }
}
