package com.example.catalogue.enquiry;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/enquiries. Validated on the server; checks in the app are only a convenience.
 * customerName may not contain control characters (such as line breaks); message may.
 */
public record EnquiryRequest(
        @NotNull @Positive
        Integer productId,

        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[^\\p{Cntrl}]*", message = "must not contain line breaks or control characters")
        String customerName,

        @NotBlank @Email @Size(max = 254)
        String email,

        @Size(max = 2000)
        String message) {
}
