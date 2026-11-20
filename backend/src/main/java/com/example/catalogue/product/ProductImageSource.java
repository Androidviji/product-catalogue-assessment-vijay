package com.example.catalogue.product;

/**
 * Fetches product image bytes from the product source.
 * Kept separate from ProductSource so each interface stays small (one method each).
 */
public interface ProductImageSource {

    /**
     * Fetches the image at a URL that came from the product source (Product.sourceImageUrl),
     * never from the client.
     *
     * @throws ProductImageNotFoundException    if the URL is not trusted or the image does not exist
     * @throws ProductImageUnavailableException if the image cannot be fetched right now
     */
    ProductImage fetchImage(String sourceImageUrl);
}
