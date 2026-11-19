package com.example.catalogue.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalogue.domain.model.CategoryProducts
import com.example.catalogue.domain.usecase.GetCategoryProductsUseCase
import com.example.catalogue.presentation.common.LoadState
import com.example.catalogue.presentation.common.toLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads one category. The name is passed exactly as the backend listed it (spaces, apostrophes). */
class CategoryViewModel(
    val categoryName: String,
    private val getCategoryProducts: GetCategoryProductsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<CategoryProducts>>(LoadState.Loading)
    val state: StateFlow<LoadState<CategoryProducts>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = getCategoryProducts(categoryName).toLoadState()
        }
    }
}
