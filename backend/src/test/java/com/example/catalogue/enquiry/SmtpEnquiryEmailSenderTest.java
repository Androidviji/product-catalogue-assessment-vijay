package com.example.catalogue.enquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEnquiryEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpEnquiryEmailSender sender = new SmtpEnquiryEmailSender(mailSender);

    private final EnquiryEmail email = new EnquiryEmail(
            "app@officeworks.test", "sales@officeworks.test", "sam@example.com", "Product enquiry: Backpack", "Body text");

    SmtpEnquiryEmailSenderTest() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void buildsAPlainTextMimeMessage() throws Exception {
        sender.send(email);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getFrom()[0].toString()).isEqualTo("app@officeworks.test");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("sales@officeworks.test");
        assertThat(message.getReplyTo()[0].toString()).isEqualTo("sam@example.com");
        assertThat(message.getSubject()).isEqualTo("Product enquiry: Backpack");
        assertThat(message.getContent()).isEqualTo("Body text");
    }

    @Test
    void smtpFailureBecomesEmailDeliveryException() {
        willThrow(new MailSendException("Mail server connection failed")).given(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(email))
                .isInstanceOf(EmailDeliveryException.class)
                .hasCauseInstanceOf(MailSendException.class);
    }
}
