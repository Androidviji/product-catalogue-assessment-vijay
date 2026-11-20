package com.example.catalogue.data

import com.example.catalogue.data.remote.CatalogueApi
import com.example.catalogue.data.remote.EnquiryRequestDto
import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.model.SubmitEnquiryResult
import com.example.catalogue.domain.repository.EnquiryRepository
import kotlinx.serialization.json.Json

/** Sends enquiries to POST /api/enquiries and translates the backend's answer. */
class EnquiryRepositoryImpl(
    private val api: CatalogueApi,
    private val json: Json,
) : EnquiryRepository {

    override suspend fun submit(enquiry: Enquiry): SubmitEnquiryResult {
        val request = EnquiryRequestDto(
            productId = enquiry.productId,
            customerName = enquiry.customerName,
            email = enquiry.email,
            message = enquiry.message.ifBlank { null },
        )
        return when (val result = safeApiCall(json) { api.submitEnquiry(request) }) {
            is DataResult.Success -> SubmitEnquiryResult.Sent(result.value.enquiryId)
            is DataResult.Failure -> fromError(result.error)
        }
    }

    private fun fromError(error: AppError): SubmitEnquiryResult = when {
        error is AppError.Validation -> fromValidation(error)
        isDefinitelyNotSent(error) -> SubmitEnquiryResult.Failed(error)
        else -> SubmitEnquiryResult.Unconfirmed(error)
    }

    /**
     * "Not sent" is claimed only with a concrete basis; anything else is Unconfirmed.
     *
     * 1. NoConnection: the socket never connected (unknown host, refused, no route), so no request
     *    bytes left the device. This holds because enquiries use a client with OkHttp's automatic
     *    retries turned off (AppContainer), so no earlier attempt can have sent the request.
     * 2. One of our backend's error codes that it returns *before* trying to send the email
     *    (JSON/content-type checks and @Valid run before the controller; the product lookup runs
     *    before EnquiryService calls the email sender). A code identifies our backend as the author,
     *    which a status code alone does not: a proxy can also return 4xx/5xx.
     *
     * Deliberately Unconfirmed:
     * - EMAIL_SEND_FAILED: the backend did try SMTP. The mail server may have accepted the message
     *   before the connection failed and the backend missed the final acknowledgement.
     * - Any 4xx/5xx without one of the codes below (a proxy's 408, 429, 502, ...), timeouts,
     *   dropped connections and unreadable 200 responses.
     */
    private fun isDefinitelyNotSent(error: AppError): Boolean = when (error) {
        AppError.NoConnection -> true
        is AppError.NotFound -> error.code in PRE_SEND_CODES
        is AppError.ServiceUnavailable -> error.code in PRE_SEND_CODES
        is AppError.Server -> error.code in PRE_SEND_CODES
        is AppError.Validation -> true // VALIDATION_FAILED is only returned by @Valid, before the service
        AppError.Timeout, AppError.ConnectionLost, AppError.UnreadableResponse -> false
    }

    /**
     * Backend field errors are shown next to the matching form field. Errors for fields the
     * form does not have (e.g. productId), or a validation error with no usable field list,
     * become a general failure, so the customer always sees something.
     */
    private fun fromValidation(error: AppError.Validation): SubmitEnquiryResult {
        val formErrors = error.fieldErrors.mapNotNull { (field, message) ->
            BACKEND_FIELDS[field]?.let { it to message.replaceFirstChar(Char::uppercase) }
        }.toMap()
        val allFieldsKnown = formErrors.size == error.fieldErrors.size
        return if (formErrors.isNotEmpty() && allFieldsKnown) {
            SubmitEnquiryResult.Invalid(formErrors)
        } else {
            SubmitEnquiryResult.Failed(error)
        }
    }

    private companion object {
        /**
         * Codes POST /api/enquiries can return before the backend attempts to send any email
         * (see backend EnquiryController, EnquiryService.submit and ApiExceptionHandler).
         * EMAIL_SEND_FAILED is intentionally absent.
         */
        val PRE_SEND_CODES = setOf(
            "MALFORMED_REQUEST",          // body not readable JSON (before the controller)
            "UNSUPPORTED_MEDIA_TYPE",     // not JSON content type (before the controller)
            "PRODUCT_NOT_FOUND",          // product lookup, before sending
            "PRODUCT_SOURCE_UNAVAILABLE", // product lookup, before sending
        )

        /** Backend JSON field name -> form field. */
        val BACKEND_FIELDS = mapOf(
            "customerName" to EnquiryField.Name,
            "email" to EnquiryField.Email,
            "message" to EnquiryField.Message,
        )
    }
}
