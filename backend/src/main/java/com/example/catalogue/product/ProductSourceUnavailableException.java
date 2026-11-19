package com.example.catalogue.product;

/** Thrown when the product source is down, slow, or returns unusable data. */
public class ProductSourceUnavailableException extends RuntimeException {

    public ProductSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
