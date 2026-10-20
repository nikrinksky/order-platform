package com.orderplatform.notification.config;

import com.orderplatform.notification.service.EmailSender;
import com.orderplatform.notification.service.LoggingEmailSender;
import com.orderplatform.notification.service.SmtpEmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSender;

/**
 * Chooses how notifications leave the process.
 *
 * <p>Which sender is active is a deployment question, not a code path: with
 * {@code notification.email.enabled=true} the SMTP sender is wired, otherwise a stand-in logs the
 * message and reports success. The flag keeps a local run - where no mail server exists - from
 * failing every order event.
 */
@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
    public EmailSender smtpEmailSender(MailSender mailSender,
                                       @Value("${notification.email.from}") String from) {
        return new SmtpEmailSender(mailSender, from);
    }

    @Bean
    @ConditionalOnProperty(name = "notification.email.enabled", havingValue = "false",
            matchIfMissing = true)
    public EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
