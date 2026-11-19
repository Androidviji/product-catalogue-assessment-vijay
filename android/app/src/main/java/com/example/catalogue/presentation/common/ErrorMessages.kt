package com.example.catalogue.presentation.common

import com.example.catalogue.domain.model.AppError

/**
 * Turns an AppError into text a customer can act on. Unknown or missing backend codes
 * fall through to a generic message, so a new backend code can never crash or confuse the app.
 * (English only; see README limitations.)
 */
fun AppError.userMessage(): String = when (this) {
    AppError.NoConnection, AppError.ConnectionLost ->
        "Can't reach the catalogue. Check your connection and try again."
    AppError.Timeout ->
        "The catalogue is taking too long to respond. Please try again."
    is AppError.NotFound -> when (code) {
        "PRODUCT_NOT_FOUND" -> "This product is no longer available."
        "CATEGORY_NOT_FOUND" -> "This category has no products right now."
        else -> "We couldn't find what you were looking for."
    }
    // EMAIL_SEND_FAILED is shown as "couldn't confirm" by the enquiry screen, not through this text.
    is AppError.ServiceUnavailable -> "Products are temporarily unavailable. Please try again shortly."
    is AppError.Validation ->
        "Some details weren't accepted. Please check the form and try again."
    is AppError.Server ->
        "Something went wrong (error $status). Please try again."
    AppError.UnreadableResponse ->
        "Something went wrong reading the catalogue. Please try again."
}
