package com.example.catalogue.enquiry;

/**
 * Response body for a sent enquiry. enquiryId is a reference that also appears in the
 * email and in the server log, so an enquiry can be traced without logging customer details.
 */
public record EnquiryResponse(String enquiryId, String status) {
}
