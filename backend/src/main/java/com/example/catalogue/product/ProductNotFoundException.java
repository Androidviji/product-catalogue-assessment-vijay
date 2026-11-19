package com.example.catalogue.product;

/** No product with this ID exists in the product source. Mapped to HTTP 404. */
public class ProductNotFoundException extends RuntimeException {

    private final int productId;

    public ProductNotFoundException(int productId) {
        super("Product not found: " + productId);
        this.productId = productId;
    }

    public int productId() {
        return productId;
    }
}
