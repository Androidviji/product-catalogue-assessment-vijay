@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.catalogue.presentation

import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.SearchResults
import com.example.catalogue.domain.usecase.SearchProductsUseCase
import com.example.catalogue.presentation.search.SearchResultsState
import com.example.catalogue.presentation.search.SearchViewModel
import com.example.catalogue.testing.FakeCatalogueRepository
import com.example.catalogue.testing.MainDispatcherRule
import com.example.catalogue.testing.product
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeCatalogueRepository()

    private fun viewModel(initial: String) = SearchViewModel(initial, SearchProductsUseCase(repository))

    @Test
    fun `runs the initial query once and shows matches`() = runTest(main.dispatcher) {
        repository.search = { DataResult.Success(SearchResults(it, listOf(product(1), product(3)))) }

        val vm = viewModel("backpack")
        assertEquals(SearchResultsState.Searching("backpack"), vm.state.value.results)
        advanceUntilIdle()

        val found = vm.state.value.results as SearchResultsState.Found
        assertEquals(listOf(1, 3), found.products.map { it.id })
        assertEquals(listOf("backpack"), repository.searchedQueries)
    }

    @Test
    fun `typing does not search, only the Search button does`() = runTest(main.dispatcher) {
        val vm = viewModel("backpack")
        advanceUntilIdle()

        vm.onQueryChange("j")
        vm.onQueryChange("ja")
        vm.onQueryChange("jacket")
        advanceUntilIdle()
        assertEquals(listOf("backpack"), repository.searchedQueries)

        vm.search()
        advanceUntilIdle()
        assertEquals(listOf("backpack", "jacket"), repository.searchedQueries)
    }

    @Test
    fun `no matches is its own state`() = runTest(main.dispatcher) {
        val vm = viewModel("bicycle")
        advanceUntilIdle()
        assertEquals(SearchResultsState.NoMatches("bicycle"), vm.state.value.results)
    }

    @Test
    fun `blank search shows a field error without calling the backend`() = runTest(main.dispatcher) {
        val vm = viewModel("backpack")
        advanceUntilIdle()

        vm.onQueryChange("   ")
        vm.search()
        advanceUntilIdle()

        assertNotNull(vm.state.value.queryError)
        assertEquals(listOf("backpack"), repository.searchedQueries)
        assertTrue("previous results stay visible", vm.state.value.results is SearchResultsState.NoMatches)
    }

    @Test
    fun `network error then retry repeats the submitted query`() = runTest(main.dispatcher) {
        repository.search = { DataResult.Failure(AppError.NoConnection) }
        val vm = viewModel("ssd")
        advanceUntilIdle()
        assertEquals(SearchResultsState.Failed("ssd", AppError.NoConnection), vm.state.value.results)

        repository.search = { DataResult.Success(SearchResults(it, listOf(product(9)))) }
        vm.onQueryChange("edited but not submitted")
        vm.retry()
        advanceUntilIdle()

        assertEquals(listOf("ssd", "ssd"), repository.searchedQueries)
        assertTrue(vm.state.value.results is SearchResultsState.Found)
    }
}
