package com.example.catalogue.product;

import static com.example.catalogue.product.TestProducts.product;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @MockitoBean
    private ProductImageService imageService;

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

    // --- product details ---------------------------------------------------

    @Test
    void returnsASingleProduct() throws Exception {
        given(catalogueService.getProduct(3)).willReturn(product(3));

        mockMvc.perform(get("/api/products/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.title").value("Product 3"))
                .andExpect(jsonPath("$.sourceImageUrl").doesNotExist());
    }

    @Test
    void unknownProductReturns404Problem() throws Exception {
        given(catalogueService.getProduct(999)).willThrow(new ProductNotFoundException(999));

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("No product exists with ID 999."));
    }

    @Test
    void nonNumericProductIdReturns400WithCodeAndWithoutEchoingTheValue() throws Exception {
        mockMvc.perform(get("/api/products/abc123xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                // detail names the parameter but not the rejected value (instance is just the client's own URL)
                .andExpect(jsonPath("$.detail").value("The value of 'id' is not valid."));
    }

    // --- framework and unexpected errors -------------------------------------

    @Test
    void unknownUrlReturns404WithStatusCode() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongHttpMethodReturns405WithStatusCode() throws Exception {
        mockMvc.perform(delete("/api/products/featured"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedErrorReturnsGeneric500WithoutInternalDetails() throws Exception {
        given(catalogueService.getProduct(1)).willThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Something went wrong. Please try again later."))
                .andExpect(result -> assertNoLeak(result.getResponse().getContentAsString(), "secret internal detail"))
                .andExpect(result -> assertNoLeak(result.getResponse().getContentAsString(), "IllegalStateException"));
    }

    // --- search --------------------------------------------------------------

    @Test
    void searchReturnsTrimmedQueryAndMatches() throws Exception {
        given(catalogueService.search("  ssd ")).willReturn(List.of(product(3)));

        mockMvc.perform(get("/api/products/search").param("q", "  ssd "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("ssd"))
                .andExpect(jsonPath("$.products.length()").value(1))
                .andExpect(jsonPath("$.products[0].id").value(3));
    }

    @Test
    void searchWithNoMatchesReturnsEmptyList() throws Exception {
        given(catalogueService.search("bicycle")).willReturn(List.of());

        mockMvc.perform(get("/api/products/search").param("q", "bicycle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isEmpty());
    }

    @Test
    void blankOrMissingSearchReturns400() throws Exception {
        given(catalogueService.search(null)).willThrow(new InvalidSearchQueryException("Enter some text to search for."));

        mockMvc.perform(get("/api/products/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"))
                .andExpect(jsonPath("$.detail").value("Enter some text to search for."));
    }

    // --- image ---------------------------------------------------------------

    @Test
    void imageReturnsBytesWithContentTypeAndCacheHeaders() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        given(imageService.getImage(1)).willReturn(new ProductImage(png, "image/png"));

        mockMvc.perform(get("/api/products/1/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png))
                .andExpect(header().string("Cache-Control", "max-age=3600"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void imageForUnknownProductReturns404() throws Exception {
        given(imageService.getImage(999)).willThrow(new ProductNotFoundException(999));

        mockMvc.perform(get("/api/products/999/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void missingImageReturns404WithoutInternalDetails() throws Exception {
        given(imageService.getImage(1)).willThrow(
                new ProductImageNotFoundException("Image URL is not on the trusted product source host: evil.example"));

        mockMvc.perform(get("/api/products/1/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"))
                .andExpect(result -> assertNoLeak(result.getResponse().getContentAsString(), "evil.example"));
    }

    @Test
    void upstreamImageFailureReturns503WithoutInternalDetails() throws Exception {
        given(imageService.getImage(1)).willThrow(
                new ProductImageUnavailableException("Fake Store image request failed: Connection refused", null));

        mockMvc.perform(get("/api/products/1/image"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IMAGE_UNAVAILABLE"))
                .andExpect(result -> assertNoLeak(result.getResponse().getContentAsString(), "Connection refused"));
    }

    private static void assertNoLeak(String body, String secret) {
        if (body.contains(secret)) {
            throw new AssertionError("Response leaked internal details: " + body);
        }
    }
}
