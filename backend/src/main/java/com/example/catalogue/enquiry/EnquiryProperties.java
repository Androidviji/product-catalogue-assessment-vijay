package com.example.catalogue.enquiry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Enquiry settings (bound from "enquiry.*" in application.yml).
 * The app refuses to start without them, so a missing inbox is caught immediately.
 */
@ConfigurationProperties("enquiry")
public record EnquiryProperties(String salesInbox, String fromAddress) {

    public EnquiryProperties {
        if (salesInbox == null || salesInbox.isBlank()) {
            throw new IllegalArgumentException("enquiry.sales-inbox must be set");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("enquiry.from-address must be set");
        }
    }
}
