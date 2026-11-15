package com.orderplatform.notification.integration;

import com.orderplatform.notification.AbstractNotificationIntegrationTest;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.model.UserContact;
import com.orderplatform.notification.repository.NotificationRepository;
import com.orderplatform.notification.repository.UserContactRepository;
import com.orderplatform.notification.service.EmailSender;
import com.orderplatform.notification.service.NotificationRetryJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Retry of a delivery that the mail server refused.
 *
 * <p>Runs against a real MongoDB: the job relies on a query over {@code sent} and
 * {@code nextAttemptAt}, and a stand-in repository would not prove that the filter selects the
 * right records.
 */
class NotificationRetryIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private NotificationRetryJob retryJob;

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
     * The defect this guards: a refused message left the record unsent with nothing to pick it up,
     * so the user never received the notification.
     */
    @Test
    void shouldDeliverNotificationThatFailedEarlier() {
        saveUnsent("notif-1", 1, LocalDateTime.now().minusMinutes(1));

        retryJob.retryUnsent();

        Notification delivered = notificationRepository.findById("notif-1").orElseThrow();
        assertThat(delivered.isSent()).isTrue();
        assertThat(delivered.getSentAt()).isNotNull();
        assertThat(delivered.getNextAttemptAt()).isNull();
        assertThat(delivered.getLastError()).isNull();
        verify(emailSender).send(eq("buyer@example.com"), anyString(), anyString());
    }

    /** A retry that fails again has to be rescheduled, not lost. */
    @Test
    void shouldRescheduleWhenTheRetryFailsAgain() {
        saveUnsent("notif-1", 1, LocalDateTime.now().minusMinutes(1));
        doThrow(new RuntimeException("mailbox unavailable"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        retryJob.retryUnsent();

        Notification kept = notificationRepository.findById("notif-1").orElseThrow();
        assertThat(kept.isSent()).isFalse();
        assertThat(kept.getAttempts()).isEqualTo(2);
        assertThat(kept.getLastError()).isEqualTo("mailbox unavailable");
        assertThat(kept.getNextAttemptAt()).isAfter(LocalDateTime.now());
    }

    /** A notification that is not due yet must not be attempted. */
    @Test
    void shouldLeaveNotificationsThatAreNotDue() {
        saveUnsent("notif-1", 1, LocalDateTime.now().plusHours(1));

        retryJob.retryUnsent();

        Notification untouched = notificationRepository.findById("notif-1").orElseThrow();
        assertThat(untouched.getAttempts()).isEqualTo(1);
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    /** A record that already went out must never be sent a second time. */
    @Test
    void shouldNotResendDeliveredNotification() {
        notificationRepository.save(Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .subject("Order Created")
                .body("body")
                .channel("EMAIL")
                .sent(true)
                .sentAt(LocalDateTime.now().minusMinutes(5))
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build());

        retryJob.retryUnsent();

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    private void saveUnsent(String id, int attempts, LocalDateTime nextAttemptAt) {
        notificationRepository.save(Notification.builder()
                .id(id)
                .userId("user-1")
                .type("ORDER_CREATED")
                .subject("Order Created: ORD-ABC12345")
                .body("Your order has been created.")
                .channel("EMAIL")
                .sent(false)
                .attempts(attempts)
                .lastError("connection refused")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .nextAttemptAt(nextAttemptAt)
                .build());
    }
}
