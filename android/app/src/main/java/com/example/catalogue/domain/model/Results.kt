package com.example.catalogue.domain.model

/** Result of a repository call: either a value or a failure the UI can explain. No exceptions cross layers. */
sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val error: AppError) : DataResult<Nothing>
}

/**
 * Failures the app understands, translated from network exceptions and the backend's
 * problem-details errors by the data layer. `code` is the backend's error code when
 * one was sent; it may be null or a code this app does not know, and the UI must cope.
 */
sealed interface AppError {
    /** Could not connect to the backend (offline, wrong address, backend not running). Nothing was sent. */
    data object NoConnection : AppError

    /** Connected, but the connection broke before a full response arrived. The request may have been processed. */
    data object ConnectionLost : AppError

    /** The backend did not answer in time. */
    data object Timeout : AppError

    /** HTTP 404, for example PRODUCT_NOT_FOUND or CATEGORY_NOT_FOUND. */
    data class NotFound(val code: String?) : AppError

    /** HTTP 503: an upstream dependency (product source, mail server) is temporarily down. */
    data class ServiceUnavailable(val code: String?) : AppError

    /** HTTP 400 VALIDATION_FAILED, with the backend's field name -> message map. */
    data class Validation(val fieldErrors: Map<String, String>) : AppError

    /** Any other HTTP error, including unknown or missing codes. */
    data class Server(val status: Int, val code: String?) : AppError

    /** The backend answered, but the response could not be read. */
    data object UnreadableResponse : AppError
}
