package com.example.catalogue.product;

import java.util.List;

/** Response body for GET /api/categories/{category}/products. */
public record CategoryProductsResponse(
        String category,
        List<ProductResponse> topRated,
        List<ProductResponse> otherProducts) {

    static CategoryProductsResponse from(CategoryProducts result) {
        return new CategoryProductsResponse(
                result.category(),
                result.topRated().stream().map(ProductResponse::from).toList(),
                result.otherProducts().stream().map(ProductResponse::from).toList());
    }
}
