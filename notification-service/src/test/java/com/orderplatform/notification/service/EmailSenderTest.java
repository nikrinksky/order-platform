package com.orderplatform.notification.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailSenderTest {

    private final MailSender mailSender = mock(MailSender.class);

    @Test
    void smtpSender_shouldHandTheMessageToTheMailServer() {
        new SmtpEmailSender(mailSender, "no-reply@order-platform.local")
                .send("buyer@example.com", "Order Created", "Your order is on its way");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertEquals("no-reply@order-platform.local", sent.getValue().getFrom());
        assertArrayEquals(new String[] {"buyer@example.com"}, sent.getValue().getTo());
        assertEquals("Order Created", sent.getValue().getSubject());
        assertEquals("Your order is on its way", sent.getValue().getText());
    }

    /**
     * A refused message must reach the caller: the notification flow decides what to record based
     * on whether this method returned.
     */
    @Test
    void smtpSender_shouldPropagateAFailedSend() {
        doThrow(new MailSendException("connection refused"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> new SmtpEmailSender(mailSender, "from@example.com")
                .send("buyer@example.com", "subject", "body"));
    }

    /**
     * With delivery switched off nothing is sent, and the stand-in has to report success so the
     * flow keeps marking the notification as handled rather than retrying forever.
     */
    @Test
    void loggingSender_shouldNotTouchTheMailServer() {
        assertDoesNotThrow(() -> new LoggingEmailSender()
                .send("buyer@example.com", "subject", "body"));

        verifyNoInteractions(mailSender);
    }
}
