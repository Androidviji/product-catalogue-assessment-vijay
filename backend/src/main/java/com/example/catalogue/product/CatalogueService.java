package com.example.catalogue.product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Catalogue business rules. Knows nothing about HTTP or Fake Store;
 * it only talks to the ProductSource interface.
 */
@Service
public class CatalogueService {

    private static final Logger log = LoggerFactory.getLogger(CatalogueService.class);

    private final ProductSource productSource;
    private final CatalogueProperties properties;

    public CatalogueService(ProductSource productSource, CatalogueProperties properties) {
        this.productSource = productSource;
        this.properties = properties;
    }

    /**
     * Returns the products the business owner chose to feature, in the configured order.
     *
     * Missing-product rule: if a configured ID does not exist in the product source,
     * it is left out, reported in missingProductIds, and logged as a warning.
     * The remaining products are still returned, so one bad ID does not break the home page,
     * and the response never pretends that all configured products were found.
     */
    public FeaturedProducts getFeaturedProducts() {
        Map<Integer, Product> productsById = productSource.findAll().stream()
                .collect(Collectors.toMap(Product::id, Function.identity(), (first, duplicate) -> first));

        List<Product> found = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (Integer id : properties.featuredProductIds()) {
            Product product = productsById.get(id);
            if (product != null) {
                found.add(product);
            } else {
                missing.add(id);
            }
        }

        if (!missing.isEmpty()) {
            log.warn("Featured product IDs not found in product source: {}", missing);
        }
        return new FeaturedProducts(List.copyOf(found), List.copyOf(missing));
    }
}
