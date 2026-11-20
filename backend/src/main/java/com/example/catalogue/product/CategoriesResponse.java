package com.example.catalogue.product;

import java.util.List;

/** Response body for GET /api/categories. */
public record CategoriesResponse(List<String> categories) {
}
