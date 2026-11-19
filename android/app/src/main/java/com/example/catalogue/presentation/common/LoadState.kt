package com.example.catalogue.presentation.common

import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.DataResult

/** The state of one piece of screen content. "Empty" is decided by each screen from the loaded data. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val data: T) : LoadState<T>
    data class Failed(val error: AppError) : LoadState<Nothing>
}

fun <T> DataResult<T>.toLoadState(): LoadState<T> = when (this) {
    is DataResult.Success -> LoadState.Loaded(value)
    is DataResult.Failure -> LoadState.Failed(error)
}
