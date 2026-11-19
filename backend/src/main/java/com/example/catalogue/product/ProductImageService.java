package com.example.catalogue.product;

import org.springframework.stereotype.Service;

/**
 * Looks up a product's image by product ID. The image URL always comes from the
 * product source, never from the client, so the client cannot make the backend
 * fetch an arbitrary URL.
 */
@Service
public class ProductImageService {

    private final CatalogueService catalogueService;
    private final ProductImageSource imageSource;

    public ProductImageService(CatalogueService catalogueService, ProductImageSource imageSource) {
        this.catalogueService = catalogueService;
        this.imageSource = imageSource;
    }

    /**
     * @throws ProductNotFoundException         if the product does not exist
     * @throws ProductImageNotFoundException    if the product has no usable image
     * @throws ProductImageUnavailableException if the image cannot be fetched right now
     */
    public ProductImage getImage(int productId) {
        Product product = catalogueService.getProduct(productId);
        String url = product.sourceImageUrl();
        if (url == null || url.isBlank()) {
            throw new ProductImageNotFoundException("Product " + productId + " has no image URL");
        }
        return imageSource.fetchImage(url);
    }
}
