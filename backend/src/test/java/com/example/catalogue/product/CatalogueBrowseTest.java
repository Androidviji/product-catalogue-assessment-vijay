package com.example.catalogue.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Categories, search and product lookup in CatalogueService. */
class CatalogueBrowseTest {

    private static Product product(int id, String category, double rating, int ratingCount) {
        return new Product(id, "Product " + id, new BigDecimal("10.00"), "Description " + id,
                category, "https://images.example/" + id + ".png", rating, ratingCount);
    }

    private static Product described(int id, String title, String description, String category) {
        return new Product(id, title, new BigDecimal("10.00"), description, category, null, 4.0, 10);
    }

    private static CatalogueService serviceWith(Product... products) {
        return new CatalogueService(() -> List.of(products), new CatalogueProperties(List.of(1)));
    }

    // --- categories -------------------------------------------------------

    @Test
    void listsDistinctCategoriesAlphabetically() {
        CatalogueService service = serviceWith(
                product(1, "jewelery", 4, 1), product(2, "electronics", 4, 1), product(3, "jewelery", 4, 1));

        assertThat(service.getCategories()).containsExactly("electronics", "jewelery");
    }

    @Test
    void topThreeByRatingThenTheRestWithNoOverlap() {
        CatalogueService service = serviceWith(
                product(1, "electronics", 3.0, 10),
                product(2, "electronics", 4.5, 10),
                product(3, "jewelery", 5.0, 10),        // other category: never included
                product(4, "electronics", 2.0, 10),
                product(5, "electronics", 4.9, 10),
                product(6, "electronics", 4.0, 10));

        CategoryProducts result = service.getCategoryProducts("electronics");

        assertThat(result.topRated()).extracting(Product::id).containsExactly(5, 2, 6);
        assertThat(result.otherProducts()).extracting(Product::id).containsExactly(1, 4);
        assertThat(result.topRated()).doesNotContainAnyElementsOf(result.otherProducts());
        assertThat(result.topRated().size() + result.otherProducts().size()).isEqualTo(5);
    }

    @Test
    void ratingTiesAreBrokenByRatingCountThenLowestId() {
        // Real Fake Store data has a tie: products 11 and 12 are both rated 4.8.
        CatalogueService service = serviceWith(
                product(11, "electronics", 4.8, 319),
                product(12, "electronics", 4.8, 400),   // same rating, more ratings: first
                product(20, "electronics", 4.0, 50),
                product(14, "electronics", 4.0, 50),    // same rating and count: lower ID first
                product(9, "electronics", 3.0, 50));

        CategoryProducts result = service.getCategoryProducts("electronics");

        assertThat(result.topRated()).extracting(Product::id).containsExactly(12, 11, 14);
        assertThat(result.otherProducts()).extracting(Product::id).containsExactly(20, 9);
    }

    @Test
    void orderDoesNotDependOnSourceOrder() {
        Product a = product(1, "x", 4.0, 5);
        Product b = product(2, "x", 4.0, 5);
        Product c = product(3, "x", 4.0, 5);
        Product d = product(4, "x", 4.0, 5);

        assertThat(serviceWith(d, c, b, a).getCategoryProducts("x"))
                .isEqualTo(serviceWith(a, b, c, d).getCategoryProducts("x"));
    }

    @Test
    void categoryWithThreeOrFewerProductsHasNoOtherProducts() {
        CategoryProducts result = serviceWith(product(1, "x", 1, 1), product(2, "x", 2, 1))
                .getCategoryProducts("x");

        assertThat(result.topRated()).extracting(Product::id).containsExactly(2, 1);
        assertThat(result.otherProducts()).isEmpty();
    }

    @Test
    void categoryMatchIgnoresCaseAndSurroundingSpacesAndReturnsSourceSpelling() {
        CategoryProducts result = serviceWith(product(1, "men's clothing", 4, 1))
                .getCategoryProducts("  MEN'S Clothing ");

        assertThat(result.category()).isEqualTo("men's clothing");
        assertThat(result.topRated()).hasSize(1);
    }

    @Test
    void unknownOrBlankCategoryIsNotFound() {
        CatalogueService service = serviceWith(product(1, "electronics", 4, 1));

        assertThatThrownBy(() -> service.getCategoryProducts("toys")).isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> service.getCategoryProducts("  ")).isInstanceOf(CategoryNotFoundException.class);
        assertThatThrownBy(() -> service.getCategoryProducts("electronic")).isInstanceOf(CategoryNotFoundException.class);
    }

    // --- search -----------------------------------------------------------

    private final CatalogueService searchService = serviceWith(
            described(1, "Fjallraven Backpack", "Fits 15 inch laptops", "men's clothing"),
            described(2, "Gold Ring", "Solid gold", "jewelery"),
            described(3, "SSD Drive", "Fast storage for your laptop", "electronics"),
            described(4, "Rain Jacket", "Waterproof", "women's clothing"));

    @Test
    void matchesTitleDescriptionOrCategoryIgnoringCase() {
        assertThat(searchService.search("BACKPACK")).extracting(Product::id).containsExactly(1);   // title
        assertThat(searchService.search("solid")).extracting(Product::id).containsExactly(2);      // description
        assertThat(searchService.search("Electronics")).extracting(Product::id).containsExactly(3); // category
        assertThat(searchService.search("laptop")).extracting(Product::id).containsExactly(1, 3);  // source order
    }

    @Test
    void matchesTheCompleteTrimmedTextAsOnePhrase() {
        assertThat(searchService.search("  gold ring  ")).extracting(Product::id).containsExactly(2);
        // Both words appear in product 2, but not as the phrase "ring gold".
        assertThat(searchService.search("ring gold")).isEmpty();
        // Substring matching: "women's clothing" contains "men's clothing", so both match (documented limitation).
        assertThat(searchService.search("men's clothing")).extracting(Product::id).containsExactly(1, 4);
    }

    @Test
    void noMatchesGivesAnEmptyList() {
        assertThat(searchService.search("bicycle")).isEmpty();
    }

    @Test
    void blankMissingOrTooLongQueryIsRejected() {
        assertThatThrownBy(() -> searchService.search(null)).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> searchService.search("")).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> searchService.search("   ")).isInstanceOf(InvalidSearchQueryException.class);
        assertThatThrownBy(() -> searchService.search("a".repeat(101))).isInstanceOf(InvalidSearchQueryException.class);
        assertThat(searchService.search("a".repeat(100))).isEmpty();
    }

    // --- product by ID ----------------------------------------------------

    @Test
    void findsProductById() {
        assertThat(searchService.getProduct(3).title()).isEqualTo("SSD Drive");
    }

    @Test
    void unknownProductIdIsNotFound() {
        assertThatThrownBy(() -> searchService.getProduct(999))
                .isInstanceOf(ProductNotFoundException.class)
                .extracting(e -> ((ProductNotFoundException) e).productId()).isEqualTo(999);
    }
}
