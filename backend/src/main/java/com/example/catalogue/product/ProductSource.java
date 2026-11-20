package com.example.catalogue.product;

import java.util.List;

/**
 * Where product data comes from. Fake Store today; a database or PIM later.
 * Swapping the source means writing a new implementation of this interface.
 */
public interface ProductSource {

    /**
     * Returns every product in the catalogue.
     *
     * @throws ProductSourceUnavailableException if the source cannot be reached
     *         or returns something we cannot understand
     */
    List<Product> findAll();
}
