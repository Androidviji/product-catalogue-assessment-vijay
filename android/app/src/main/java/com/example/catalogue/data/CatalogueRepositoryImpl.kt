package com.example.catalogue.data

import com.example.catalogue.data.remote.CatalogueApi
import com.example.catalogue.data.remote.ProductDto
import com.example.catalogue.domain.model.CategoryProducts
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.FeaturedProducts
import com.example.catalogue.domain.model.Product
import com.example.catalogue.domain.model.SearchResults
import com.example.catalogue.domain.repository.CatalogueRepository
import kotlinx.serialization.json.Json

/** Catalogue data from the backend. Maps DTOs to domain models and failures to AppError. */
class CatalogueRepositoryImpl(
    private val api: CatalogueApi,
    private val baseUrl: String,
    private val json: Json,
) : CatalogueRepository {

    override suspend fun getFeaturedProducts(): DataResult<FeaturedProducts> =
        safeApiCall(json) {
            val dto = api.getFeaturedProducts()
            FeaturedProducts(
                products = dto.products.map { it.toDomain() },
                configuredCount = dto.configuredCount,
                missingCount = dto.missingProductIds.size,
            )
        }

    override suspend fun getCategories(): DataResult<List<String>> =
        safeApiCall(json) { api.getCategories().categories }

    override suspend fun getCategoryProducts(category: String): DataResult<CategoryProducts> =
        safeApiCall(json) {
            val dto = api.getCategoryProducts(category)
            CategoryProducts(
                category = dto.category,
                topRated = dto.topRated.map { it.toDomain() },
                otherProducts = dto.otherProducts.map { it.toDomain() },
            )
        }

    override suspend fun searchProducts(query: String): DataResult<SearchResults> =
        safeApiCall(json) {
            val dto = api.searchProducts(query)
            SearchResults(dto.query, dto.products.map { it.toDomain() })
        }

    override suspend fun getProduct(id: Int): DataResult<Product> =
        safeApiCall(json) { api.getProduct(id).toDomain() }

    private fun ProductDto.toDomain() = Product(
        id = id,
        title = title,
        price = price,
        description = description,
        category = category,
        rating = rating,
        ratingCount = ratingCount,
        imageUrl = CatalogueApi.imageUrl(baseUrl, id),
    )
}
