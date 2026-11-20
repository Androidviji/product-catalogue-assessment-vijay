package com.example.catalogue.enquiry;

/**
 * Sends enquiry emails. SMTP today (SmtpEnquiryEmailSender); another provider
 * (for example an HTTP email API) can be added by writing a new implementation.
 */
public interface EnquiryEmailSender {

    /**
     * Returns only after the provider has accepted the message.
     *
     * @throws EmailDeliveryException if the provider did not accept it
     */
    void send(EnquiryEmail email);
}
