package com.orderplatform.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSender;

/**
 * Sends notifications over SMTP.
 *
 * <p>The {@link MailSender} abstraction is used instead of {@code JavaMailSenderImpl} directly so
 * the mail server stays swappable and the class stays testable without a socket.
 *
 * <p>Failures propagate: the caller treats a failed send as a failed notification and leaves it
 * unsent, so the message is not reported as delivered when it never left the process.
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final MailSender mailSender;
    private final String from;

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("Sent notification email to {}", to);
    }
}
