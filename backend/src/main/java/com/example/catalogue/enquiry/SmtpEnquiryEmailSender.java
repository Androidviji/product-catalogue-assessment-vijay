package com.example.catalogue.enquiry;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends enquiry emails over SMTP using Spring's JavaMailSender (configured by spring.mail.*).
 * JavaMailSender.send returns only after the SMTP server has accepted the message,
 * and throws MailException otherwise.
 */
@Component
public class SmtpEnquiryEmailSender implements EnquiryEmailSender {

    private final JavaMailSender mailSender;

    public SmtpEnquiryEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EnquiryEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(email.from());
            helper.setTo(email.to());
            helper.setReplyTo(email.replyTo());
            helper.setSubject(email.subject());
            helper.setText(email.body(), false);
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            throw new EmailDeliveryException("SMTP server did not accept the enquiry email", e);
        }
    }
}
