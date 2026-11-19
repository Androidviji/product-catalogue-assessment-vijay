package com.example.catalogue.product;

import java.util.List;

/**
 * Products in one category, split into two groups that never overlap:
 * topRated:      up to three highest-rated products
 * otherProducts: every remaining product in the category
 * Both groups use the same order (see CatalogueService.BY_RATING).
 */
public record CategoryProducts(String category, List<Product> topRated, List<Product> otherProducts) {
}
