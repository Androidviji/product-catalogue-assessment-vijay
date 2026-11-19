package com.example.catalogue.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalogue.domain.model.FeaturedProducts
import com.example.catalogue.domain.usecase.GetCategoriesUseCase
import com.example.catalogue.domain.usecase.GetFeaturedProductsUseCase
import com.example.catalogue.domain.usecase.SearchProductsUseCase
import com.example.catalogue.presentation.common.LoadState
import com.example.catalogue.presentation.common.toLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Featured products and categories load independently, so a category failure does not
 * hide the featured products (and the other way round). Each has its own Retry.
 */
data class HomeUiState(
    val featured: LoadState<FeaturedProducts> = LoadState.Loading,
    val categories: LoadState<List<String>> = LoadState.Loading,
    val searchQuery: String = "",
    val searchError: String? = null,
)

class HomeViewModel(
    private val getFeaturedProducts: GetFeaturedProductsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val searchProducts: SearchProductsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadFeatured()
        loadCategories()
    }

    fun loadFeatured() {
        _state.update { it.copy(featured = LoadState.Loading) }
        viewModelScope.launch {
            val result = getFeaturedProducts().toLoadState()
            _state.update { it.copy(featured = result) }
        }
    }

    fun loadCategories() {
        _state.update { it.copy(categories = LoadState.Loading) }
        viewModelScope.launch {
            val result = getCategories().toLoadState()
            _state.update { it.copy(categories = result) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query, searchError = null) }
    }

    /**
     * Called when the Search button is tapped. Returns the trimmed query to open the search
     * screen with, or null (and shows a message under the field) if the query is invalid.
     */
    fun onSearchClicked(): String? {
        val error = searchProducts.validate(_state.value.searchQuery)
        _state.update { it.copy(searchError = error) }
        return if (error == null) _state.value.searchQuery.trim() else null
    }
}
