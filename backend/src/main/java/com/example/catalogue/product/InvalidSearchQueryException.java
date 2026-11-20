package com.example.catalogue.product;

/** The search text is blank or too long. Mapped to HTTP 400; the message is safe to show the client. */
public class InvalidSearchQueryException extends RuntimeException {

    public InvalidSearchQueryException(String message) {
        super(message);
    }
}
