package com.example.catalogue.domain

import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.model.SubmitEnquiryResult
import com.example.catalogue.domain.usecase.EnquiryValidator
import com.example.catalogue.domain.usecase.SearchProductsUseCase
import com.example.catalogue.domain.usecase.SubmitEnquiryUseCase
import com.example.catalogue.testing.FakeCatalogueRepository
import com.example.catalogue.testing.FakeEnquiryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnquiryValidatorTest {

    private val validator = EnquiryValidator()
    private fun enquiry(name: String = "Sam Lee", email: String = "sam@example.com", message: String = "") =
        Enquiry(1, name, email, message)

    @Test
    fun `valid enquiry has no errors and message is optional`() {
        assertTrue(validator.validate(enquiry()).isEmpty())
        assertTrue(validator.validate(enquiry(name = "  Sam  ", email = " sam@example.com ")).isEmpty())
    }

    @Test
    fun `blank name and email are required`() {
        val errors = validator.validate(enquiry(name = "   ", email = ""))
        assertEquals(setOf(EnquiryField.Name, EnquiryField.Email), errors.keys)
    }

    @Test
    fun `invalid email formats are rejected`() {
        listOf("sam", "sam@", "@example.com", "sam@example", "sam smith@example.com").forEach {
            assertTrue("should reject '$it'", EnquiryField.Email in validator.validate(enquiry(email = it)))
        }
    }

    @Test
    fun `length limits match the backend`() {
        assertTrue(validator.validate(enquiry(name = "a".repeat(100))).isEmpty())
        assertTrue(EnquiryField.Name in validator.validate(enquiry(name = "a".repeat(101))))
        assertTrue(validator.validate(enquiry(message = "m".repeat(2000))).isEmpty())
        assertTrue(EnquiryField.Message in validator.validate(enquiry(message = "m".repeat(2001))))
        assertTrue(EnquiryField.Email in validator.validate(enquiry(email = "a".repeat(250) + "@x.com")))
    }

    @Test
    fun `name with a line break is rejected`() {
        assertTrue(EnquiryField.Name in validator.validate(enquiry(name = "Sam\nBcc: x@example.com")))
    }
}

class SubmitEnquiryUseCaseTest {

    private val repository = FakeEnquiryRepository()
    private val useCase = SubmitEnquiryUseCase(repository, EnquiryValidator())

    @Test
    fun `invalid enquiry is not sent to the backend`() = runTest {
        val result = useCase(Enquiry(1, "", "bad", ""))

        assertTrue(result is SubmitEnquiryResult.Invalid)
        assertTrue(repository.submitted.isEmpty())
    }

    @Test
    fun `valid enquiry is trimmed and sent`() = runTest {
        val result = useCase(Enquiry(7, "  Sam Lee ", " sam@example.com ", "  Hi  "))

        assertEquals(SubmitEnquiryResult.Sent("ref-1"), result)
        assertEquals(Enquiry(7, "Sam Lee", "sam@example.com", "Hi"), repository.submitted.single())
    }
}

class SearchProductsUseCaseTest {

    private val repository = FakeCatalogueRepository()
    private val useCase = SearchProductsUseCase(repository)

    @Test
    fun `blank or too long queries are invalid`() {
        assertTrue(useCase.validate("") != null)
        assertTrue(useCase.validate("   ") != null)
        assertTrue(useCase.validate("a".repeat(101)) != null)
        assertNull(useCase.validate("a".repeat(100)))
        assertNull(useCase.validate("  backpack "))
    }

    @Test
    fun `query is trimmed before it is sent`() = runTest {
        useCase("  men's clothing  ")
        assertEquals(listOf("men's clothing"), repository.searchedQueries)
    }
}
