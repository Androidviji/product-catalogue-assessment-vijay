package com.example.catalogue.enquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.catalogue.product.CatalogueProperties;
import com.example.catalogue.product.CatalogueService;
import com.example.catalogue.product.Product;
import com.example.catalogue.product.ProductNotFoundException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EnquiryServiceTest {

    private static final Product BACKPACK = new Product(1, "Fjallraven Backpack", new BigDecimal("109.95"),
            "A backpack", "men's clothing", "https://fakestoreapi.com/img/1.png", 3.9, 120);

    private final CatalogueService catalogue =
            new CatalogueService(() -> List.of(BACKPACK), new CatalogueProperties(List.of(1)));
    private final EnquiryProperties properties = new EnquiryProperties("sales@officeworks.test", "app@officeworks.test");

    private final List<EnquiryEmail> sent = new ArrayList<>();
    private final EnquiryEmailSender recordingSender = sent::add;

    private final EnquiryRequest request =
            new EnquiryRequest(1, "  Sam Lee ", " sam@example.com ", "Is this in stock?");

    @Test
    void sendsOneEmailWithProductAndCustomerDetailsToTheSalesInbox() {
        String enquiryId = new EnquiryService(catalogue, recordingSender, properties).submit(request);

        assertThat(sent).hasSize(1);
        EnquiryEmail email = sent.get(0);
        assertThat(email.to()).isEqualTo("sales@officeworks.test");
        assertThat(email.from()).isEqualTo("app@officeworks.test");
        assertThat(email.replyTo()).isEqualTo("sam@example.com");
        assertThat(email.subject()).isEqualTo("Product enquiry: Fjallraven Backpack (product 1)");
        assertThat(email.body()).contains(
                "ID:       1", "Fjallraven Backpack", "men's clothing", "$109.95",
                "Name:  Sam Lee", "Email: sam@example.com", "Is this in stock?",
                "Enquiry reference: " + enquiryId);
        assertThat(enquiryId).isNotBlank();
    }

    @Test
    void missingMessageIsShownAsNoMessage() {
        new EnquiryService(catalogue, recordingSender, properties)
                .submit(new EnquiryRequest(1, "Sam", "sam@example.com", null));

        assertThat(sent.get(0).body()).contains("(no message)");
    }

    @Test
    void unknownProductSendsNothing() {
        EnquiryService service = new EnquiryService(catalogue, recordingSender, properties);

        assertThatThrownBy(() -> service.submit(new EnquiryRequest(99, "Sam", "sam@example.com", null)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThat(sent).isEmpty();
    }

    @Test
    void emailFailureIsReportedAndLoggedWithoutCustomerDetails(CapturedOutput output) {
        // A provider error that echoes the customer's address and a credential, as real SMTP errors can.
        EnquiryEmailSender failing = email -> {
            throw new EmailDeliveryException("SMTP server did not accept the enquiry email",
                    new ConnectException("550 rejected Reply-To <sam@example.com> for user smtp-user"));
        };
        EnquiryService service = new EnquiryService(catalogue, failing, properties);

        assertThatThrownBy(() -> service.submit(request)).isInstanceOf(EmailDeliveryException.class);

        assertThat(output).contains("for product 1 was not sent: ConnectException");
        assertThat(output).doesNotContain("Sam Lee", "sam@example.com", "Is this in stock?", "550", "smtp-user");
    }

    @Test
    void successIsLoggedWithoutCustomerDetails(CapturedOutput output) {
        String enquiryId = new EnquiryService(catalogue, recordingSender, properties).submit(request);

        assertThat(output).contains("Enquiry " + enquiryId + " for product 1 accepted by the mail server");
        assertThat(output).doesNotContain("Sam Lee", "sam@example.com", "Is this in stock?");
    }
}
