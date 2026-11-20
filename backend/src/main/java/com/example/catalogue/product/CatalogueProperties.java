package com.example.catalogue.product;

import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business settings for the catalogue (bound from "catalogue.*" in application.yml).
 *
 * featuredProductIds: the products the business owner has chosen for the home page,
 * in display order. They are configured, not calculated from ratings.
 * The app refuses to start if the list is empty or contains duplicates,
 * so a configuration mistake is caught immediately rather than shown to customers.
 */
@ConfigurationProperties("catalogue")
public record CatalogueProperties(List<Integer> featuredProductIds) {

    public CatalogueProperties {
        if (featuredProductIds == null || featuredProductIds.isEmpty()) {
            throw new IllegalArgumentException("catalogue.featured-product-ids must list at least one product ID");
        }
        if (new HashSet<>(featuredProductIds).size() != featuredProductIds.size()) {
            throw new IllegalArgumentException("catalogue.featured-product-ids contains duplicates: " + featuredProductIds);
        }
        featuredProductIds = List.copyOf(featuredProductIds);
    }
}
