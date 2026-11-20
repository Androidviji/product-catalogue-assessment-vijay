package com.example.catalogue.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductImageServiceTest {

    private final List<String> requestedUrls = new ArrayList<>();
    private final ProductImageSource recordingSource = url -> {
        requestedUrls.add(url);
        return new ProductImage(new byte[] {1, 2, 3}, "image/png");
    };

    private ProductImageService serviceWith(Product... products) {
        CatalogueService catalogue = new CatalogueService(() -> List.of(products), new CatalogueProperties(List.of(1)));
        return new ProductImageService(catalogue, recordingSource);
    }

    private static Product withImage(int id, String imageUrl) {
        return new Product(id, "P" + id, BigDecimal.ONE, "d", "c", imageUrl, 4.0, 1);
    }

    @Test
    void fetchesTheImageUrlStoredOnTheProduct() {
        ProductImage image = serviceWith(withImage(1, "https://fakestoreapi.com/img/1.png"), withImage(2, "https://fakestoreapi.com/img/2.png"))
                .getImage(2);

        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(requestedUrls).containsExactly("https://fakestoreapi.com/img/2.png");
    }

    @Test
    void unknownProductIsNotFoundAndNothingIsFetched() {
        assertThatThrownBy(() -> serviceWith(withImage(1, "https://fakestoreapi.com/img/1.png")).getImage(99))
                .isInstanceOf(ProductNotFoundException.class);
        assertThat(requestedUrls).isEmpty();
    }

    @Test
    void productWithoutImageUrlIsImageNotFound() {
        assertThatThrownBy(() -> serviceWith(withImage(1, null)).getImage(1))
                .isInstanceOf(ProductImageNotFoundException.class);
        assertThatThrownBy(() -> serviceWith(withImage(1, " ")).getImage(1))
                .isInstanceOf(ProductImageNotFoundException.class);
        assertThat(requestedUrls).isEmpty();
    }
}
