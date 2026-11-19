package com.example.catalogue.product;

import static com.example.catalogue.product.TestProducts.product;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogueService catalogueService;

    @Test
    void listsCategories() throws Exception {
        given(catalogueService.getCategories()).willReturn(List.of("electronics", "men's clothing"));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0]").value("electronics"))
                .andExpect(jsonPath("$.categories[1]").value("men's clothing"));
    }

    @Test
    void returnsTopRatedAndOtherProductsForAUrlEncodedCategory() throws Exception {
        given(catalogueService.getCategoryProducts("men's clothing")).willReturn(new CategoryProducts(
                "men's clothing", List.of(product(3), product(2), product(1)), List.of(product(4))));

        // URI, not a String template: a String would be encoded again (% becomes %25).
        mockMvc.perform(get(URI.create("/api/categories/men%27s%20clothing/products")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("men's clothing"))
                .andExpect(jsonPath("$.topRated.length()").value(3))
                .andExpect(jsonPath("$.topRated[0].id").value(3))
                .andExpect(jsonPath("$.otherProducts.length()").value(1))
                .andExpect(jsonPath("$.otherProducts[0].id").value(4));
    }

    @Test
    void unknownCategoryReturns404Problem() throws Exception {
        given(catalogueService.getCategoryProducts("toys")).willThrow(new CategoryNotFoundException("toys"));

        mockMvc.perform(get("/api/categories/toys/products"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }
}
