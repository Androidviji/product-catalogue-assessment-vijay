package com.example.catalogue.product;

import java.math.BigDecimal;

/**
 * Our own product model. Every ProductSource maps its data into this shape,
 * so the rest of the backend never depends on Fake Store's JSON.
 *
 * sourceImageUrl is kept internal: it is not part of the API contract.
 * A later stage will serve images through /api/products/{id}/image.
 */
public record Product(
        int id,
        String title,
        BigDecimal price,
        String description,
        String category,
        String sourceImageUrl,
        double rating,
        int ratingCount) {
}
