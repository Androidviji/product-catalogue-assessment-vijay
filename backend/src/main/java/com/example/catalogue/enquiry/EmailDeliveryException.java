package com.example.catalogue.enquiry;

/** The email provider did not accept the message. Mapped to HTTP 503 with a generic message. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
