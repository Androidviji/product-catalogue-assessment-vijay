package com.example.catalogue.product;

import java.util.List;

/** Response body for GET /api/products/search. query is the trimmed text that was matched. */
public record SearchResponse(String query, List<ProductResponse> products) {
}
