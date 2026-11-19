package com.example.catalogue.domain.usecase

import com.example.catalogue.domain.model.CategoryProducts
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.FeaturedProducts
import com.example.catalogue.domain.model.Product
import com.example.catalogue.domain.model.SearchResults
import com.example.catalogue.domain.repository.CatalogueRepository

/*
 * One class per user-facing operation. Most simply delegate to the repository today;
 * they exist so ViewModels depend on named operations, and so rules have an obvious home
 * (see SearchProductsUseCase). If a use case never gains logic, it is cheap to remove.
 */

class GetFeaturedProductsUseCase(private val repository: CatalogueRepository) {
    suspend operator fun invoke(): DataResult<FeaturedProducts> = repository.getFeaturedProducts()
}

class GetCategoriesUseCase(private val repository: CatalogueRepository) {
    suspend operator fun invoke(): DataResult<List<String>> = repository.getCategories()
}

class GetCategoryProductsUseCase(private val repository: CatalogueRepository) {
    suspend operator fun invoke(category: String): DataResult<CategoryProducts> =
        repository.getCategoryProducts(category)
}

class GetProductDetailsUseCase(private val repository: CatalogueRepository) {
    suspend operator fun invoke(productId: Int): DataResult<Product> = repository.getProduct(productId)
}

/**
 * Search rules shared by the home and search screens. They mirror the backend
 * (trimmed, not blank, at most 100 characters), so obviously invalid searches never
 * hit the network. The backend still enforces the same rules.
 */
class SearchProductsUseCase(private val repository: CatalogueRepository) {

    /** A message to show under the search field, or null if the query can be searched. */
    fun validate(rawQuery: String): String? {
        val query = rawQuery.trim()
        return when {
            query.isEmpty() -> "Enter a product name, description or category."
            query.length > MAX_QUERY_LENGTH -> "Search text must be $MAX_QUERY_LENGTH characters or fewer."
            else -> null
        }
    }

    /** Call only after [validate] returned null. */
    suspend operator fun invoke(rawQuery: String): DataResult<SearchResults> =
        repository.searchProducts(rawQuery.trim())

    companion object {
        const val MAX_QUERY_LENGTH = 100
    }
}
