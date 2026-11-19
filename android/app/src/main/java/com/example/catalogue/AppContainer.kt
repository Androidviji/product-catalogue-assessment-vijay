package com.example.catalogue

import com.example.catalogue.data.CatalogueRepositoryImpl
import com.example.catalogue.data.EnquiryRepositoryImpl
import com.example.catalogue.data.remote.CatalogueApi
import com.example.catalogue.domain.repository.CatalogueRepository
import com.example.catalogue.domain.repository.EnquiryRepository
import com.example.catalogue.domain.usecase.EnquiryValidator
import com.example.catalogue.domain.usecase.GetCategoriesUseCase
import com.example.catalogue.domain.usecase.GetCategoryProductsUseCase
import com.example.catalogue.domain.usecase.GetFeaturedProductsUseCase
import com.example.catalogue.domain.usecase.GetProductDetailsUseCase
import com.example.catalogue.domain.usecase.SearchProductsUseCase
import com.example.catalogue.domain.usecase.SubmitEnquiryUseCase
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual dependency injection: builds every object once, wiring them through constructors.
 * Created by CatalogueApplication, so it lives as long as the app process.
 * A DI framework (Hilt) would do the same wiring with annotations; for this app size,
 * plain Kotlin is easier to read and explain.
 */
class AppContainer(
    baseUrl: String,
    /** Shared by Retrofit and the image loader. Tests pass a client with short timeouts. */
    val okHttpClient: OkHttpClient = defaultHttpClient(),
) {

    private val json = Json {
        ignoreUnknownKeys = true // additive backend changes do not break the app
        explicitNulls = false    // an absent optional message is omitted, not sent as null
    }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    /** Catalogue reads (GETs). OkHttp may retry these after a connection failure, which is harmless. */
    private val api: CatalogueApi = retrofit.create(CatalogueApi::class.java)

    /**
     * Enquiries (POST) must never be sent twice by OkHttp itself. With the default
     * retryOnConnectionFailure = true, OkHttp resends a request whose connection fails after it was
     * written (for example a stale keep-alive connection), which could email the sales team twice.
     * newBuilder() shares the connection pool and threads; only the retry setting differs.
     */
    private val enquiryApi: CatalogueApi = retrofit.newBuilder()
        .client(okHttpClient.newBuilder().retryOnConnectionFailure(false).build())
        .build()
        .create(CatalogueApi::class.java)

    private val catalogueRepository: CatalogueRepository = CatalogueRepositoryImpl(api, baseUrl, json)
    private val enquiryRepository: EnquiryRepository = EnquiryRepositoryImpl(enquiryApi, json)

    val getFeaturedProducts = GetFeaturedProductsUseCase(catalogueRepository)
    val getCategories = GetCategoriesUseCase(catalogueRepository)
    val getCategoryProducts = GetCategoryProductsUseCase(catalogueRepository)
    val searchProducts = SearchProductsUseCase(catalogueRepository)
    val getProductDetails = GetProductDetailsUseCase(catalogueRepository)
    val submitEnquiry = SubmitEnquiryUseCase(enquiryRepository, EnquiryValidator())

    companion object {
        /**
         * The backend itself waits up to ~5s for Fake Store and up to ~15s for SMTP,
         * so the read timeout leaves room for that.
         */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
