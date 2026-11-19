package com.example.catalogue.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalogue.domain.model.Product
import com.example.catalogue.domain.usecase.GetProductDetailsUseCase
import com.example.catalogue.presentation.common.LoadState
import com.example.catalogue.presentation.common.toLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads the product fresh from GET /api/products/{id}, so details are current even if the list was old. */
class ProductDetailsViewModel(
    private val productId: Int,
    private val getProductDetails: GetProductDetailsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<Product>>(LoadState.Loading)
    val state: StateFlow<LoadState<Product>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch {
            _state.value = getProductDetails(productId).toLoadState()
        }
    }
}
