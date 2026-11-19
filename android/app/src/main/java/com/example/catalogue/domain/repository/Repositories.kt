package com.example.catalogue.domain.repository

import com.example.catalogue.domain.model.CategoryProducts
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.FeaturedProducts
import com.example.catalogue.domain.model.Product
import com.example.catalogue.domain.model.SearchResults
import com.example.catalogue.domain.model.SubmitEnquiryResult

/**
 * What the app needs from "the catalogue". Implemented in the data layer (backend over HTTP);
 * replaced by fakes in tests.
 */
interface CatalogueRepository {
    suspend fun getFeaturedProducts(): DataResult<FeaturedProducts>
    suspend fun getCategories(): DataResult<List<String>>
    suspend fun getCategoryProducts(category: String): DataResult<CategoryProducts>
    suspend fun searchProducts(query: String): DataResult<SearchResults>
    suspend fun getProduct(id: Int): DataResult<Product>
}

interface EnquiryRepository {
    suspend fun submit(enquiry: Enquiry): SubmitEnquiryResult
}
