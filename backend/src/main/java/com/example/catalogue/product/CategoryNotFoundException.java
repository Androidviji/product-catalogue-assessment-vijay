package com.example.catalogue.product;

/** No product in the source has this category. Mapped to HTTP 404. */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String category) {
        super("Category not found: " + category);
    }
}
