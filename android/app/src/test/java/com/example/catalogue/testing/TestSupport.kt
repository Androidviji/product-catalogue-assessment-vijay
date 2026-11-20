package com.example.catalogue.testing

import com.example.catalogue.domain.model.CategoryProducts
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.FeaturedProducts
import com.example.catalogue.domain.model.Product
import com.example.catalogue.domain.model.SearchResults
import com.example.catalogue.domain.model.SubmitEnquiryResult
import com.example.catalogue.domain.repository.CatalogueRepository
import com.example.catalogue.domain.repository.EnquiryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** viewModelScope runs on Dispatchers.Main; tests replace it with a controllable test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun product(id: Int, title: String = "Product $id", category: String = "electronics") = Product(
    id = id, title = title, price = 10.0, description = "Description $id", category = category,
    rating = 4.0, ratingCount = 10, imageUrl = "http://test/api/products/$id/image",
)

/** A repository whose answers each test sets. Counts calls so tests can check what was requested. */
class FakeCatalogueRepository : CatalogueRepository {
    var featured: DataResult<FeaturedProducts> = DataResult.Success(FeaturedProducts(listOf(product(1)), 1, 0))
    var categories: DataResult<List<String>> = DataResult.Success(listOf("electronics"))
    var categoryProducts: DataResult<CategoryProducts> =
        DataResult.Success(CategoryProducts("electronics", listOf(product(1)), emptyList()))
    var search: (String) -> DataResult<SearchResults> = { DataResult.Success(SearchResults(it, emptyList())) }
    var productResult: DataResult<Product> = DataResult.Success(product(1))

    val searchedQueries = mutableListOf<String>()
    var featuredCalls = 0
    var categoryRequests = mutableListOf<String>()

    override suspend fun getFeaturedProducts() = featured.also { featuredCalls++ }
    override suspend fun getCategories() = categories
    override suspend fun getCategoryProducts(category: String) = categoryProducts.also { categoryRequests += category }
    override suspend fun searchProducts(query: String): DataResult<SearchResults> {
        searchedQueries += query
        return search(query)
    }
    override suspend fun getProduct(id: Int) = productResult
}

/** By default answers immediately; set [gate] to hold the answer until the test completes it. */
class FakeEnquiryRepository : EnquiryRepository {
    var result: SubmitEnquiryResult = SubmitEnquiryResult.Sent("ref-1")
    var gate: CompletableDeferred<Unit>? = null
    val submitted = mutableListOf<Enquiry>()

    override suspend fun submit(enquiry: Enquiry): SubmitEnquiryResult {
        submitted += enquiry
        gate?.await()
        return result
    }
}
