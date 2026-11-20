package com.example.catalogue.product;

import java.util.List;

/**
 * Result of looking up the configured featured products.
 *
 * products:          the configured products that exist, in configured order
 * missingProductIds: configured IDs the product source does not have
 */
public record FeaturedProducts(List<Product> products, List<Integer> missingProductIds) {

    public int configuredCount() {
        return products.size() + missingProductIds.size();
    }
}
