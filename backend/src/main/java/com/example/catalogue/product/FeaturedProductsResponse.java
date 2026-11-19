package com.example.catalogue.product;

import java.util.List;

/**
 * Response body for GET /api/products/featured.
 * configuredCount and missingProductIds make it explicit when fewer products
 * are returned than were configured.
 */
public record FeaturedProductsResponse(
        int configuredCount,
        List<ProductResponse> products,
        List<Integer> missingProductIds) {

    static FeaturedProductsResponse from(FeaturedProducts featured) {
        return new FeaturedProductsResponse(
                featured.configuredCount(),
                featured.products().stream().map(ProductResponse::from).toList(),
                featured.missingProductIds());
    }
}
