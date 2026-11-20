package com.example.catalogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Starts the whole application with the real FakeStoreProductSource pointed at a
 * port where nothing is listening, proving the full chain
 * controller -> service -> source -> error handler returns a clean 503.
 * No internet access is needed.
 */
@SpringBootTest(properties = {
        "fake-store.base-url=http://127.0.0.1:1",
        "fake-store.connect-timeout=500ms",
        "fake-store.read-timeout=500ms"
})
@AutoConfigureMockMvc
class FeaturedProductsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unreachableProductSourceReturns503() throws Exception {
        mockMvc.perform(get("/api/products/featured"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRODUCT_SOURCE_UNAVAILABLE"));
    }
}
