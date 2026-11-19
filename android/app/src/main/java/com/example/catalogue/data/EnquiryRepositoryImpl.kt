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

    /**
     * Backend field errors are shown next to the matching form field. Errors for fields the
     * form does not have (e.g. productId), or a validation error with no usable field list,
     * become a general failure, so the customer always sees something.
     */
    private fun fromError(error: AppError): SubmitEnquiryResult {
        if (error !is AppError.Validation) return SubmitEnquiryResult.Failed(error)
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
        /** Backend JSON field name -> form field. */
        val BACKEND_FIELDS = mapOf(
            "customerName" to EnquiryField.Name,
            "email" to EnquiryField.Email,
            "message" to EnquiryField.Message,
        )
    }
}
