package com.example.catalogue.data.remote

import kotlinx.serialization.Serializable

/*
 * DTOs: the exact JSON shapes of the backend API. Used only in the data layer and
 * mapped to domain models by the repositories. Unknown JSON fields are ignored
 * (see AppContainer's Json configuration), so additive backend changes do not break the app.
 */

@Serializable
data class ProductDto(
    val id: Int,
    val title: String,
    val price: Double,
    val description: String = "",
    val category: String = "",
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
)

@Serializable
data class FeaturedProductsDto(
    val configuredCount: Int,
    val products: List<ProductDto>,
    val missingProductIds: List<Int> = emptyList(),
)

@Serializable
data class CategoriesDto(val categories: List<String>)

@Serializable
data class CategoryProductsDto(
    val category: String,
    val topRated: List<ProductDto>,
    val otherProducts: List<ProductDto>,
)

@Serializable
data class SearchResponseDto(val query: String, val products: List<ProductDto>)

@Serializable
data class EnquiryRequestDto(
    val productId: Int,
    val customerName: String,
    val email: String,
    /** Omitted from the JSON when null (explicitNulls = false). */
    val message: String? = null,
)

@Serializable
data class EnquiryResponseDto(val enquiryId: String, val status: String)

/** The backend's error body (RFC 9457 problem details). Every field is optional so a partial body still parses. */
@Serializable
data class ProblemDto(
    val status: Int? = null,
    val title: String? = null,
    val detail: String? = null,
    val code: String? = null,
    val errors: Map<String, String>? = null,
)
