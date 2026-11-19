package com.example.catalogue.product;

import static com.example.catalogue.product.TestProducts.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogueServiceTest {

    // Source order is deliberately different from the configured order.
    private final ProductSource source = () -> List.of(product(1), product(2), product(3), product(4), product(5), product(6));

    private CatalogueService serviceFeaturing(Integer... ids) {
        return new CatalogueService(source, new CatalogueProperties(List.of(ids)));
    }

    @Test
    void returnsExactlyTheConfiguredProductsInConfiguredOrder() {
        FeaturedProducts result = serviceFeaturing(5, 1, 4, 2, 6).getFeaturedProducts();

        assertThat(result.products()).extracting(Product::id).containsExactly(5, 1, 4, 2, 6);
        assertThat(result.missingProductIds()).isEmpty();
        assertThat(result.configuredCount()).isEqualTo(5);
    }

    @Test
    void doesNotChooseProductsByRating() {
        ProductSource ratedSource = () -> List.of(
                withRating(1, 1.0), withRating(2, 5.0), withRating(3, 4.9), withRating(4, 4.8));
        CatalogueService service = new CatalogueService(ratedSource, new CatalogueProperties(List.of(1, 4)));

        assertThat(service.getFeaturedProducts().products()).extracting(Product::id).containsExactly(1, 4);
    }

    @Test
    void reportsMissingProductsAndStillReturnsTheRestInOrder() {
        FeaturedProducts result = serviceFeaturing(3, 99, 1, 42, 2).getFeaturedProducts();

        assertThat(result.products()).extracting(Product::id).containsExactly(3, 1, 2);
        assertThat(result.missingProductIds()).containsExactly(99, 42);
        assertThat(result.configuredCount()).isEqualTo(5);
    }

    @Test
    void returnsNoProductsWhenNoneOfTheConfiguredIdsExist() {
        FeaturedProducts result = serviceFeaturing(98, 99).getFeaturedProducts();

        assertThat(result.products()).isEmpty();
        assertThat(result.missingProductIds()).containsExactly(98, 99);
    }

    @Test
    void propagatesProductSourceFailure() {
        ProductSource failing = () -> {
            throw new ProductSourceUnavailableException("down", null);
        };
        CatalogueService service = new CatalogueService(failing, new CatalogueProperties(List.of(1)));

        assertThatThrownBy(service::getFeaturedProducts).isInstanceOf(ProductSourceUnavailableException.class);
    }

    @Test
    void rejectsEmptyOrDuplicateConfiguration() {
        assertThatThrownBy(() -> new CatalogueProperties(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CatalogueProperties(List.of(1, 2, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Product withRating(int id, double rating) {
        Product p = product(id);
        return new Product(p.id(), p.title(), p.price(), p.description(), p.category(), p.sourceImageUrl(), rating, 10);
    }
}
