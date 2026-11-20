package com.example.catalogue.domain.model

/**
 * Domain models: what the app works with. They do not mirror the backend JSON
 * (that is the DTOs' job in the data layer), so the API can change without touching the UI.
 */
data class Product(
    val id: Int,
    val title: String,
    val price: Double,
    val description: String,
    val category: String,
    val rating: Double,
    val ratingCount: Int,
    /** Backend image endpoint for this product, built by the data layer. */
    val imageUrl: String,
)

/**
 * The featured products the backend could find, plus how many were configured.
 * When some are missing, the home screen says so instead of implying all loaded.
 */
data class FeaturedProducts(
    val products: List<Product>,
    val configuredCount: Int,
    val missingCount: Int,
) {
    val isIncomplete: Boolean get() = missingCount > 0
}

/** A category's products, already split by the backend into two groups that never overlap. */
data class CategoryProducts(
    val category: String,
    val topRated: List<Product>,
    val otherProducts: List<Product>,
)

data class SearchResults(val query: String, val products: List<Product>)

/** What the customer typed in the enquiry form. */
data class Enquiry(
    val productId: Int,
    val customerName: String,
    val email: String,
    val message: String,
)

enum class EnquiryField { Name, Email, Message }

/** Outcome of submitting an enquiry. */
sealed interface SubmitEnquiryResult {
    /** The backend confirmed the sales inbox's mail server accepted the email. */
    data class Sent(val enquiryId: String) : SubmitEnquiryResult

    /** One or more fields are invalid (found by the app's own checks or by the backend). */
    data class Invalid(val fieldErrors: Map<EnquiryField, String>) : SubmitEnquiryResult

    /**
     * Definitely not sent: the backend said so (e.g. EMAIL_SEND_FAILED), or the app could not connect at all.
     * Sending again is safe. The form should be kept.
     */
    data class Failed(val error: AppError) : SubmitEnquiryResult

    /**
     * Unknown outcome: the request may have reached the backend, but no reliable answer came back
     * (timeout, connection dropped, unexpected server error). The email may or may not have been sent,
     * so sending again could create a duplicate. The form should be kept and the customer told.
     */
    data class Unconfirmed(val error: AppError) : SubmitEnquiryResult
}
