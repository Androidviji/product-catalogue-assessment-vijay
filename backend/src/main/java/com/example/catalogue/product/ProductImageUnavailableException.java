package com.example.catalogue.product;

/**
 * The image could not be fetched right now: network failure, timeout, upstream error,
 * unexpected content type or an image that is too large. Mapped to HTTP 503.
 */
public class ProductImageUnavailableException extends RuntimeException {

    public ProductImageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
