package com.example.catalogue.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The backend's HTTP API (see backend/README.md). Paths are relative to the base URL,
 * which ends with "/". Retrofit URL-encodes @Path and @Query values, so category names
 * such as "men's clothing" are sent correctly.
 */
interface CatalogueApi {

    @GET("api/products/featured")
    suspend fun getFeaturedProducts(): FeaturedProductsDto

    @GET("api/categories")
    suspend fun getCategories(): CategoriesDto

    @GET("api/categories/{category}/products")
    suspend fun getCategoryProducts(@Path("category") category: String): CategoryProductsDto

    @GET("api/products/search")
    suspend fun searchProducts(@Query("q") query: String): SearchResponseDto

    @GET("api/products/{id}")
    suspend fun getProduct(@Path("id") id: Int): ProductDto

    @POST("api/enquiries")
    suspend fun submitEnquiry(@Body request: EnquiryRequestDto): EnquiryResponseDto

    companion object {
        /** The image endpoint is loaded by the image library (Coil), not Retrofit. */
        fun imageUrl(baseUrl: String, productId: Int): String = "${baseUrl}api/products/$productId/image"
    }
}
