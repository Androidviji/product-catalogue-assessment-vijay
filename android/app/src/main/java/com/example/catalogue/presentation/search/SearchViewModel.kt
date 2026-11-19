package com.example.catalogue.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.Product
import com.example.catalogue.domain.usecase.SearchProductsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchResultsState {
    data object Idle : SearchResultsState
    data class Searching(val query: String) : SearchResultsState
    data class Found(val query: String, val products: List<Product>) : SearchResultsState
    data class NoMatches(val query: String) : SearchResultsState
    data class Failed(val query: String, val error: AppError) : SearchResultsState
}

data class SearchUiState(
    /** What is in the text field. Changing it does not search. */
    val query: String = "",
    val queryError: String? = null,
    val results: SearchResultsState = SearchResultsState.Idle,
)

/**
 * Searches only when [search] is called (the Search button), never while typing.
 * The search screen opens with the query typed on the home screen and runs it once.
 */
class SearchViewModel(
    initialQuery: String,
    private val searchProducts: SearchProductsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(query = initialQuery))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var lastSubmittedQuery: String? = null

    init {
        search()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, queryError = null) }
    }

    /** Search button tapped. */
    fun search() {
        val error = searchProducts.validate(_state.value.query)
        if (error != null) {
            _state.update { it.copy(queryError = error) }
            return
        }
        run(_state.value.query.trim())
    }

    /** Retry the last search that was submitted, even if the field has been edited since. */
    fun retry() {
        lastSubmittedQuery?.let(::run)
    }

    private fun run(query: String) {
        lastSubmittedQuery = query
        searchJob?.cancel() // a newer search replaces an older one still in flight
        _state.update { it.copy(queryError = null, results = SearchResultsState.Searching(query)) }
        searchJob = viewModelScope.launch {
            val results = when (val result = searchProducts(query)) {
                is DataResult.Success ->
                    if (result.value.products.isEmpty()) SearchResultsState.NoMatches(query)
                    else SearchResultsState.Found(query, result.value.products)
                is DataResult.Failure -> SearchResultsState.Failed(query, result.error)
            }
            _state.update { it.copy(results = results) }
        }
    }
}
