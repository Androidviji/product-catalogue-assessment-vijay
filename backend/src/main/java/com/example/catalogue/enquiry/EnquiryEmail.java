package com.example.catalogue.enquiry;

/**
 * A provider-neutral email: everything any email provider needs, nothing SMTP-specific.
 * replyTo is the customer's address, so sales can reply directly.
 */
public record EnquiryEmail(String from, String to, String replyTo, String subject, String body) {
}
