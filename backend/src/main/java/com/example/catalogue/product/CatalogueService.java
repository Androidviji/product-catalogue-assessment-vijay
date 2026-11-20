package com.example.catalogue.product;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    /** A single product by ID. */
    public Product getProduct(int id) {
        return productSource.findAll().stream()
                .filter(product -> product.id() == id)
                .findFirst()
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * The categories that currently have at least one product, sorted alphabetically.
     * Derived from the product list, so an empty category can never be listed.
     */
    public List<String> getCategories() {
        return productSource.findAll().stream()
                .map(Product::category)
                .filter(category -> category != null && !category.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Highest rating first. Ties are broken by more ratings (more reliable score),
     * then by lower ID, so the order is always the same for the same data.
     */
    static final Comparator<Product> BY_RATING = Comparator
            .comparingDouble(Product::rating).reversed()
            .thenComparing(Comparator.comparingInt(Product::ratingCount).reversed())
            .thenComparingInt(Product::id);

    static final int TOP_RATED_COUNT = 3;

    /**
     * Products in a category: the top three by rating, then the rest.
     * The category name is matched case-insensitively after trimming.
     * The whole category is sorted once and split, so the two groups cannot overlap.
     *
     * @throws CategoryNotFoundException if no product has this category
     */
    public CategoryProducts getCategoryProducts(String category) {
        String wanted = category == null ? "" : category.trim();
        List<Product> inCategory = productSource.findAll().stream()
                .filter(product -> product.category() != null && product.category().equalsIgnoreCase(wanted))
                .sorted(BY_RATING)
                .toList();
        if (inCategory.isEmpty()) {
            throw new CategoryNotFoundException(wanted);
        }
        int split = Math.min(TOP_RATED_COUNT, inCategory.size());
        return new CategoryProducts(
                inCategory.get(0).category(),   // the source's spelling, e.g. "men's clothing"
                inCategory.subList(0, split),
                inCategory.subList(split, inCategory.size()));
    }

    static final int MAX_SEARCH_LENGTH = 100;

    /**
     * Products whose title, description or category contains the complete trimmed query,
     * ignoring case. "usb drive" matches "USB Drive 64GB" but not "USB flash drive"
     * (the text is matched as one phrase, not as separate words). Results are in source (ID) order.
     * No matches gives an empty list.
     *
     * @throws InvalidSearchQueryException if the query is missing, blank, or longer than 100 characters
     */
    public List<Product> search(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidSearchQueryException("Enter some text to search for.");
        }
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new InvalidSearchQueryException("Search text must be " + MAX_SEARCH_LENGTH + " characters or fewer.");
        }
        String needle = trimmed.toLowerCase(Locale.ROOT);
        return productSource.findAll().stream()
                .filter(product -> contains(product.title(), needle)
                        || contains(product.description(), needle)
                        || contains(product.category(), needle))
                .toList();
    }

    private static boolean contains(String text, String lowerCaseNeedle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }
}
