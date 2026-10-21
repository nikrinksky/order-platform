package com.orderplatform.notification.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Stands in for the SMTP sender when email delivery is switched off.
 *
 * <p>Reports the message as delivered so the notification flow keeps working on a deployment
 * without a mail server - a local run or the test profile, for instance. The log line is the only
 * trace, which is why the fact that delivery was skipped is stated explicitly.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("Email delivery is disabled, notification for {} was not sent: {}", to, subject);
    }
}
