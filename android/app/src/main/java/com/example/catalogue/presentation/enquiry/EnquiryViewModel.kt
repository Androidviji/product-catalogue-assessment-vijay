package com.example.catalogue.presentation.enquiry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.model.SubmitEnquiryResult
import com.example.catalogue.domain.usecase.SubmitEnquiryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EnquiryUiState(
    val name: String = "",
    val email: String = "",
    val message: String = "",
    val fieldErrors: Map<EnquiryField, String> = emptyMap(),
    /** True while a request is in flight: the Send button is disabled and further taps are ignored. */
    val isSubmitting: Boolean = false,
    /** A failure that is not about one field (network, mail server down, unknown error). */
    val submitError: AppError? = null,
    /** Set only after the backend confirmed the email was accepted. Shows the thank-you message. */
    val sentEnquiryId: String? = null,
)

/**
 * The enquiry form. The typed values live here (not in the composable), so they survive
 * rotation and are kept when sending fails.
 */
class EnquiryViewModel(
    private val productId: Int,
    private val submitEnquiry: SubmitEnquiryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(EnquiryUiState())
    val state: StateFlow<EnquiryUiState> = _state.asStateFlow()

    fun onNameChange(value: String) =
        _state.update { it.copy(name = value, fieldErrors = it.fieldErrors - EnquiryField.Name) }

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, fieldErrors = it.fieldErrors - EnquiryField.Email) }

    fun onMessageChange(value: String) =
        _state.update { it.copy(message = value, fieldErrors = it.fieldErrors - EnquiryField.Message) }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting || current.sentEnquiryId != null) return // no duplicate sends

        // Set synchronously, before the coroutine starts, so a quick second tap is ignored.
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = submitEnquiry(
                Enquiry(productId, current.name, current.email, current.message),
            )
            _state.update {
                when (result) {
                    is SubmitEnquiryResult.Sent ->
                        it.copy(isSubmitting = false, fieldErrors = emptyMap(), sentEnquiryId = result.enquiryId)
                    is SubmitEnquiryResult.Invalid ->
                        it.copy(isSubmitting = false, fieldErrors = result.fieldErrors)
                    // The typed values are untouched, so the customer can simply try again.
                    is SubmitEnquiryResult.Failed ->
                        it.copy(isSubmitting = false, submitError = result.error)
                }
            }
        }
    }
}
