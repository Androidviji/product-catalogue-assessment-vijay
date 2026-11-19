package com.example.catalogue.product;

import static com.example.catalogue.product.TestProducts.product;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Tests the HTTP contract: JSON shape and error mapping. The service is mocked. */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogueService catalogueService;

    @Test
    void returnsFeaturedProductsInOurOwnContract() throws Exception {
        given(catalogueService.getFeaturedProducts())
                .willReturn(new FeaturedProducts(List.of(product(5), product(1)), List.of()));

        mockMvc.perform(get("/api/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredCount").value(2))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].id").value(5))
                .andExpect(jsonPath("$.products[0].title").value("Product 5"))
                .andExpect(jsonPath("$.products[0].rating").value(4.0))
                .andExpect(jsonPath("$.products[0].ratingCount").value(100))
                .andExpect(jsonPath("$.products[0].sourceImageUrl").doesNotExist())
                .andExpect(jsonPath("$.products[0].image").doesNotExist())
                .andExpect(jsonPath("$.missingProductIds").isEmpty());
    }

    @Test
    void makesMissingConfiguredProductsVisible() throws Exception {
        given(catalogueService.getFeaturedProducts())
                .willReturn(new FeaturedProducts(List.of(product(1)), List.of(99)));

        mockMvc.perform(get("/api/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuredCount").value(2))
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.missingProductIds[0]").value(99));
    }

    @Test
    void productSourceFailureReturns503ProblemWithoutInternalDetails() throws Exception {
        given(catalogueService.getFeaturedProducts())
                .willThrow(new ProductSourceUnavailableException("secret internal detail",
                        new RuntimeException("java.net.ConnectException")));

        mockMvc.perform(get("/api/products/featured"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("PRODUCT_SOURCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Products are temporarily unavailable. Please try again shortly."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (body.contains("secret internal detail") || body.contains("ConnectException")) {
                        throw new AssertionError("Response leaked internal details: " + body);
                    }
                });
    }
}
