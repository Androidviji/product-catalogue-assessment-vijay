@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.catalogue.presentation

import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.model.SubmitEnquiryResult
import com.example.catalogue.domain.usecase.EnquiryValidator
import com.example.catalogue.domain.usecase.SubmitEnquiryUseCase
import com.example.catalogue.presentation.enquiry.EnquiryViewModel
import com.example.catalogue.testing.FakeEnquiryRepository
import com.example.catalogue.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnquiryViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeEnquiryRepository()
    private val vm = EnquiryViewModel(productId = 1, SubmitEnquiryUseCase(repository, EnquiryValidator()))

    private fun fillValidForm() {
        vm.onNameChange("Sam Lee")
        vm.onEmailChange("sam@example.com")
        vm.onMessageChange("Is this in stock?")
    }

    @Test
    fun `client validation errors are shown and nothing is sent`() = runTest(main.dispatcher) {
        vm.onEmailChange("not-an-email")
        vm.submit()
        advanceUntilIdle()

        assertEquals(setOf(EnquiryField.Name, EnquiryField.Email), vm.state.value.fieldErrors.keys)
        assertTrue(repository.submitted.isEmpty())
        assertFalse(vm.state.value.isSubmitting)

        vm.onNameChange("Sam")
        assertFalse("editing a field clears its error", EnquiryField.Name in vm.state.value.fieldErrors)
    }

    @Test
    fun `thank you is shown only after backend success`() = runTest(main.dispatcher) {
        repository.gate = CompletableDeferred()
        fillValidForm()

        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.isSubmitting)
        assertNull("no thank-you before the backend answers", vm.state.value.sentEnquiryId)

        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("ref-1", vm.state.value.sentEnquiryId)
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun `duplicate taps while sending submit only once`() = runTest(main.dispatcher) {
        repository.gate = CompletableDeferred()
        fillValidForm()

        vm.submit()
        vm.submit()
        advanceUntilIdle()
        vm.submit()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        vm.submit() // after success too

        assertEquals(1, repository.submitted.size)
    }

    @Test
    fun `failure keeps the typed details and shows an error`() = runTest(main.dispatcher) {
        repository.result = SubmitEnquiryResult.Failed(AppError.ServiceUnavailable("EMAIL_SEND_FAILED"))
        fillValidForm()

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(AppError.ServiceUnavailable("EMAIL_SEND_FAILED"), state.submitError)
        assertEquals("Sam Lee", state.name)
        assertEquals("sam@example.com", state.email)
        assertEquals("Is this in stock?", state.message)
        assertNull(state.sentEnquiryId)
        assertFalse(state.isSubmitting)

        // Try again: succeeds, error cleared.
        repository.result = SubmitEnquiryResult.Sent("ref-2")
        vm.submit()
        advanceUntilIdle()
        assertEquals("ref-2", vm.state.value.sentEnquiryId)
        assertEquals(2, repository.submitted.size)
    }

    @Test
    fun `server validation errors appear on the fields`() = runTest(main.dispatcher) {
        repository.result = SubmitEnquiryResult.Invalid(mapOf(EnquiryField.Email to "Must be a well-formed email address"))
        fillValidForm()

        vm.submit()
        advanceUntilIdle()

        assertEquals("Must be a well-formed email address", vm.state.value.fieldErrors[EnquiryField.Email])
        assertNull(vm.state.value.sentEnquiryId)
    }

    @Test
    fun `unknown server error is a general failure, not a crash`() = runTest(main.dispatcher) {
        repository.result = SubmitEnquiryResult.Failed(AppError.Server(418, code = null))
        fillValidForm()

        vm.submit()
        advanceUntilIdle()

        assertEquals(AppError.Server(418, null), vm.state.value.submitError)
    }
}
