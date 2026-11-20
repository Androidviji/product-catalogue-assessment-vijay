package com.example.catalogue.product;

/** Image bytes plus their media type (for example "image/png"). */
public record ProductImage(byte[] content, String contentType) {
}
