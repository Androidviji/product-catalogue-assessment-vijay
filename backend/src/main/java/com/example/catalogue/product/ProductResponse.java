package com.example.catalogue.product;

import java.math.BigDecimal;

/**
 * A product as the Android app sees it. This is the API contract;
 * it deliberately does not include the source's image URL.
 */
public record ProductResponse(
        int id,
        String title,
        BigDecimal price,
        String description,
        String category,
        double rating,
        int ratingCount) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(),
                product.title(),
                product.price(),
                product.description(),
                product.category(),
                product.rating(),
                product.ratingCount());
    }
}
