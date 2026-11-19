package com.example.catalogue.product;

import java.math.BigDecimal;

/** Small helper for building test products. */
final class TestProducts {

    private TestProducts() {
    }

    static Product product(int id) {
        return new Product(id, "Product " + id, new BigDecimal("10.00"), "Description " + id,
                "electronics", "https://images.example/" + id + ".png", 4.0, 100);
    }
}
