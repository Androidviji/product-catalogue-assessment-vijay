package com.example.catalogue.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP layer only: translates requests into service calls and results into API responses. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogueService catalogueService;

    public ProductController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping("/featured")
    public FeaturedProductsResponse featured() {
        return FeaturedProductsResponse.from(catalogueService.getFeaturedProducts());
    }
}
