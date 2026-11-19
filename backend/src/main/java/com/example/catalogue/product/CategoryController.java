package com.example.catalogue.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP layer for categories. Category names contain spaces and apostrophes, so clients URL-encode them. */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CatalogueService catalogueService;

    public CategoryController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping
    public CategoriesResponse categories() {
        return new CategoriesResponse(catalogueService.getCategories());
    }

    @GetMapping("/{category}/products")
    public CategoryProductsResponse categoryProducts(@PathVariable String category) {
        return CategoryProductsResponse.from(catalogueService.getCategoryProducts(category));
    }
}
