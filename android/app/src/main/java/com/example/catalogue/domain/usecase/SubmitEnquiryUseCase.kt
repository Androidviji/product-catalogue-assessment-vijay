package com.example.catalogue.domain.usecase

import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.model.SubmitEnquiryResult
import com.example.catalogue.domain.repository.EnquiryRepository

/**
 * Client-side enquiry checks. They match the backend's rules so customers get instant
 * feedback; the backend validates again and its errors are shown the same way.
 */
class EnquiryValidator {

    fun validate(enquiry: Enquiry): Map<EnquiryField, String> {
        val errors = mutableMapOf<EnquiryField, String>()
        val name = enquiry.customerName.trim()
        val email = enquiry.email.trim()

        when {
            name.isEmpty() -> errors[EnquiryField.Name] = "Enter your name."
            name.length > MAX_NAME -> errors[EnquiryField.Name] = "Name must be $MAX_NAME characters or fewer."
            name.any { it.isISOControl() } -> errors[EnquiryField.Name] = "Name must be on one line."
        }
        when {
            email.isEmpty() -> errors[EnquiryField.Email] = "Enter your email address."
            email.length > MAX_EMAIL -> errors[EnquiryField.Email] = "Email must be $MAX_EMAIL characters or fewer."
            !EMAIL_PATTERN.matches(email) -> errors[EnquiryField.Email] = "Enter a valid email address, like name@example.com."
        }
        if (enquiry.message.trim().length > MAX_MESSAGE) {
            errors[EnquiryField.Message] = "Message must be $MAX_MESSAGE characters or fewer."
        }
        return errors
    }

    companion object {
        const val MAX_NAME = 100
        const val MAX_EMAIL = 254
        const val MAX_MESSAGE = 2000

        /** Deliberately simple: something@something.something, no spaces. The backend has the final say. */
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/** Validates locally first; only a valid enquiry is sent to the backend. */
class SubmitEnquiryUseCase(
    private val repository: EnquiryRepository,
    private val validator: EnquiryValidator,
) {
    suspend operator fun invoke(enquiry: Enquiry): SubmitEnquiryResult {
        val errors = validator.validate(enquiry)
        if (errors.isNotEmpty()) return SubmitEnquiryResult.Invalid(errors)

        val trimmed = enquiry.copy(
            customerName = enquiry.customerName.trim(),
            email = enquiry.email.trim(),
            message = enquiry.message.trim(),
        )
        return repository.submit(trimmed)
    }
}
