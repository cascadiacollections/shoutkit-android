package com.cascadiacollections.sir

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that installs an [UnconfinedTestDispatcher] as [Dispatchers.Main]
 * for the duration of each test. Required by any test that touches
 * `viewModelScope.launch` or other Main-dispatched coroutines.
 *
 * Any [androidx.lifecycle.ViewModel] created during the test should be
 * registered via [registerViewModel] so it is cleared (cancelling any
 * `viewModelScope` coroutines) before [Dispatchers.resetMain] runs. Without
 * this, a coroutine still running against the test's Main dispatcher can
 * race the next test's [Dispatchers.setMain] call and fail with
 * `IllegalStateException: Dispatchers.Main is used concurrently with setting it`.
 *
 * Clearing only cancels those coroutines. Under [UnconfinedTestDispatcher] a collector
 * resumed by a DataStore emission runs on DataStore's IO thread, and one that is mid-run
 * when the test ends keeps touching Main until it reaches a suspension point, so
 * [finished] also waits for every registered scope's job to complete before resetting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineRule : TestWatcher() {

    val testDispatcher = UnconfinedTestDispatcher()

    private val viewModelStore = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()
    private var nextKey = 0

    fun registerViewModel(viewModel: androidx.lifecycle.ViewModel) {
        viewModelStore.put((nextKey++).toString(), viewModel)
        viewModelJobs += viewModel.viewModelScope.coroutineContext.job
    }

    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        viewModelStore.clear()
        runBlocking { withTimeout(5_000) { viewModelJobs.joinAll() } }
        Dispatchers.resetMain()
    }
}
