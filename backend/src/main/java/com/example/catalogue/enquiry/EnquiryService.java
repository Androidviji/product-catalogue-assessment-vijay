package com.example.catalogue.enquiry;

import com.example.catalogue.product.CatalogueService;
import com.example.catalogue.product.Product;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns a validated enquiry into an email to the sales inbox.
 *
 * Logging rule: log the enquiry ID and product ID only. Never log the customer's
 * name, email address or message.
 */
@Service
public class EnquiryService {

    private static final Logger log = LoggerFactory.getLogger(EnquiryService.class);

    private final CatalogueService catalogueService;
    private final EnquiryEmailSender emailSender;
    private final EnquiryProperties properties;

    public EnquiryService(CatalogueService catalogueService, EnquiryEmailSender emailSender,
            EnquiryProperties properties) {
        this.catalogueService = catalogueService;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    /**
     * @return the enquiry ID, once the email provider has accepted the email
     * @throws com.example.catalogue.product.ProductNotFoundException if the product does not exist
     * @throws EmailDeliveryException if the email was not accepted
     */
    public String submit(EnquiryRequest request) {
        // Product details come from the trusted source, not from the client.
        Product product = catalogueService.getProduct(request.productId());
        String enquiryId = UUID.randomUUID().toString();

        EnquiryEmail email = new EnquiryEmail(
                properties.fromAddress(),
                properties.salesInbox(),
                request.email().trim(),
                "Product enquiry: " + product.title() + " (product " + product.id() + ")",
                body(enquiryId, product, request));

        try {
            emailSender.send(email);
        } catch (EmailDeliveryException e) {
            // Root cause type and message only (for example "ConnectException: Connection refused").
            Throwable root = rootCause(e);
            log.warn("Enquiry {} for product {} was not sent: {}: {}",
                    enquiryId, product.id(), root.getClass().getSimpleName(), root.getMessage());
            throw e;
        }
        log.info("Enquiry {} for product {} accepted by the mail server", enquiryId, product.id());
        return enquiryId;
    }

    static String body(String enquiryId, Product product, EnquiryRequest request) {
        String message = request.message() == null || request.message().isBlank()
                ? "(no message)"
                : request.message().trim();
        return """
                A customer has sent an enquiry from the catalogue app.

                Product
                  ID:       %d
                  Title:    %s
                  Category: %s
                  Price:    $%s

                Customer
                  Name:  %s
                  Email: %s

                Message
                %s

                Enquiry reference: %s
                """.formatted(
                product.id(), product.title(), product.category(), product.price(),
                request.customerName().trim(), request.email().trim(),
                message, enquiryId);
    }

    private static Throwable rootCause(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
