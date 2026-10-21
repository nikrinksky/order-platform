package com.orderplatform.notification.service;

/**
 * Delivers a notification to its recipient.
 *
 * <p>An interface rather than a direct {@code JavaMailSender} call, because whether a deployment
 * has a mail server at all is a configuration question: the order flow must not fail on a
 * deployment that only stores notifications.
 */
public interface EmailSender {

    /**
     * Sends one message.
     *
     * <p>An implementation must throw when the message cannot be handed over. Swallowing the
     * failure would leave the notification marked as sent when nobody ever received it, and the
     * caller relies on the exception to retry the delivery.
     *
     * @param to      recipient address
     * @param subject message subject
     * @param body    message body
     */
    void send(String to, String subject, String body);
}
