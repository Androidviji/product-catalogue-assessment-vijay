package com.example.catalogue.product;

/**
 * The product exists but has no usable image: no image URL, a URL outside the trusted
 * product source, or the source reports the image as missing. Mapped to HTTP 404.
 */
public class ProductImageNotFoundException extends RuntimeException {

    public ProductImageNotFoundException(String message) {
        super(message);
    }
}
