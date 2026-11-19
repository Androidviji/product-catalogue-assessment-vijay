package com.example.catalogue.enquiry;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalogue.product.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HTTP contract for POST /api/enquiries: validation and error mapping. The service is mocked. */
@WebMvcTest(EnquiryController.class)
class EnquiryControllerTest {

    private static final String VALID = """
            { "productId": 1, "customerName": "Sam Lee", "email": "sam@example.com", "message": "In stock?" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnquiryService enquiryService;

    private ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post("/api/enquiries").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validEnquiryReturnsSentWithReference() throws Exception {
        given(enquiryService.submit(any())).willReturn("ref-123");

        postJson(VALID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enquiryId").value("ref-123"))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void messageIsOptional() throws Exception {
        given(enquiryService.submit(any())).willReturn("ref-123");

        postJson("""
                { "productId": 1, "customerName": "Sam Lee", "email": "sam@example.com" }
                """).andExpect(status().isOk());
    }

    @Test
    void missingFieldsAreListedAndNothingIsSent() throws Exception {
        postJson("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.productId").exists())
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors", not(hasKey("message"))));

        verify(enquiryService, never()).submit(any());
    }

    @Test
    void invalidValuesAreRejected() throws Exception {
        String longMessage = "x".repeat(2001);
        postJson("""
                { "productId": 0, "customerName": "   ", "email": "not-an-email", "message": "%s" }
                """.formatted(longMessage))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productId").exists())
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.message").exists());

        verify(enquiryService, never()).submit(any());
    }

    @Test
    void nameWithLineBreakIsRejected() throws Exception {
        postJson("""
                { "productId": 1, "customerName": "Sam\\nBcc: x@example.com", "email": "sam@example.com" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerName").value("must not contain line breaks or control characters"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        postJson("{ not json").andExpect(status().isBadRequest());
        postJson("""
                { "productId": "abc", "customerName": "Sam", "email": "sam@example.com" }
                """).andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductReturns404() throws Exception {
        given(enquiryService.submit(any())).willThrow(new ProductNotFoundException(1));

        postJson(VALID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void emailFailureReturnsSafe503() throws Exception {
        given(enquiryService.submit(any())).willThrow(new EmailDeliveryException(
                "SMTP server did not accept the enquiry email",
                new RuntimeException("535 Authentication failed for user smtp-user password hunter2")));

        postJson(VALID)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_SEND_FAILED"))
                .andExpect(jsonPath("$.detail").value("Your enquiry could not be sent right now. Please try again later."))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    for (String secret : new String[] {"SMTP", "535", "hunter2", "smtp-user", "sam@example.com"}) {
                        if (body.contains(secret)) {
                            throw new AssertionError("Response leaked '" + secret + "': " + body);
                        }
                    }
                });
    }
}
