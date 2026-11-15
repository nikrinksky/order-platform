package com.orderplatform.notification.integration;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.notification.AbstractNotificationIntegrationTest;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.model.UserContact;
import com.orderplatform.notification.repository.NotificationRepository;
import com.orderplatform.notification.repository.UserContactRepository;
import com.orderplatform.notification.service.EmailSender;
import com.orderplatform.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * What a redelivered event does to a notification that never reached the mail server.
 *
 * <p>Runs against a real MongoDB: the unique index on {@code eventId} is what keeps the second
 * copy out, and only the real server enforces it.
 */
class NotificationRedeliveryIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserContactRepository userContactRepository;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userContactRepository.deleteAll();
        userContactRepository.save(UserContact.builder()
                .userId("user-1")
                .email("buyer@example.com")
                .build());
    }

    /**
     * The defect this guards: the redelivery saw the stored record and dropped the event, so an
     * unsent notification waited for the scheduled retry even though a free attempt had just
     * arrived.
     */
    @Test
    void shouldDeliverTheStoredNotificationWhenTheEventArrivesAgain() {
        notificationRepository.save(unsent("notif-1", "event-1")
                // Not due for a while: only the redelivery can deliver it now
                .nextAttemptAt(LocalDateTime.now().plusHours(1))
                .build());

        notificationService.onOrderCreated(orderCreated(), "event-1");

        List<Notification> stored = notificationRepository.findByUserId("user-1");
        assertThat(stored).hasSize(1);
        Notification delivered = stored.get(0);
        assertThat(delivered.isSent()).isTrue();
        assertThat(delivered.getSentAt()).isNotNull();
        assertThat(delivered.getAttempts()).isEqualTo(2);
        assertThat(delivered.getLastError()).isNull();
        assertThat(delivered.getNextAttemptAt()).isNull();
        verify(emailSender).send(eq("buyer@example.com"), anyString(), anyString());
    }

    /** A notification that already went out must not be sent a second time. */
    @Test
    void shouldNotSendAgainWhenTheEventArrivesAfterDelivery() {
        notificationRepository.save(unsent("notif-1", "event-1")
                .sent(true)
                .sentAt(LocalDateTime.now().minusMinutes(1))
                .build());

        notificationService.onOrderCreated(orderCreated(), "event-1");

        assertThat(notificationRepository.findByUserId("user-1")).hasSize(1);
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    /** The attempts are bounded: a record that gave up is left for inspection, not retried. */
    @Test
    void shouldNotSpendAnotherAttemptWhenTheyRunOut() {
        notificationRepository.save(unsent("notif-1", "event-1")
                .attempts(5)
                .build());

        notificationService.onOrderCreated(orderCreated(), "event-1");

        Notification kept = notificationRepository.findById("notif-1").orElseThrow();
        assertThat(kept.isSent()).isFalse();
        assertThat(kept.getAttempts()).isEqualTo(5);
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    private static Notification.NotificationBuilder unsent(String id, String eventId) {
        return Notification.builder()
                .id(id)
                .userId("user-1")
                .eventId(eventId)
                .type("ORDER_CREATED")
                .subject("Order Created: ORD-ABC12345")
                .body("Your order has been created.")
                .channel("EMAIL")
                .sent(false)
                .attempts(1)
                .lastError("connection refused")
                .createdAt(LocalDateTime.now().minusMinutes(5));
    }

    private static OrderCreatedEvent orderCreated() {
        return OrderCreatedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setTotalAmount(ByteBuffer.wrap(
                        new BigDecimal("99.99").unscaledValue().toByteArray()))
                .build();
    }
}
