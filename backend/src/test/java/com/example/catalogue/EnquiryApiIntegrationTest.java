package com.example.catalogue;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalogue.product.CatalogueService;
import com.example.catalogue.product.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Whole application with the real SMTP sender pointed at a port where nothing listens.
 * Proves that when the mail server does not accept the message, the client gets a 503,
 * never a success. The catalogue is mocked so no internet access is needed.
 */
@SpringBootTest(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1",
        "spring.mail.properties.mail.smtp.connectiontimeout=500"
})
@AutoConfigureMockMvc
class EnquiryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogueService catalogueService;

    @Test
    void unreachableSmtpServerReturns503NotSuccess() throws Exception {
        given(catalogueService.getProduct(1)).willReturn(new Product(1, "Backpack", new BigDecimal("109.95"),
                "A backpack", "men's clothing", "https://fakestoreapi.com/img/1.png", 3.9, 120));

        mockMvc.perform(post("/api/enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": 1, "customerName": "Sam Lee", "email": "sam@example.com" }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_SEND_FAILED"));
    }
}
