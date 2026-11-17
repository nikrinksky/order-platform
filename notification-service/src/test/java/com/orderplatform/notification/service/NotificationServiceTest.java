package com.orderplatform.notification.service;

import com.orderplatform.events.order.OrderCreatedEvent;
import com.orderplatform.events.order.OrderStatusChangedEvent;
import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.model.UserContact;
import com.orderplatform.notification.repository.NotificationRepository;
import com.orderplatform.notification.repository.UserContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserContactRepository userContactRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private NotificationRateLimiter rateLimiter;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        // Field-injected policy values; the retry behaviour below is what depends on them
        ReflectionTestUtils.setField(notificationService, "maxAttempts", 3);
        ReflectionTestUtils.setField(notificationService, "backoffMs", 1000L);
        // The limit has its own tests; here the bucket always has room unless a test says otherwise
        lenient().when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
    }

    @Test
    void onOrderCreated_shouldCreateNotification() {
        OrderCreatedEvent event = orderCreated();

        Notification savedNotification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .subject("Order Created: ORD-ABC12345")
                .sent(false)
                .build();

        when(notificationRepository.findByEventId("event-1")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        assertDoesNotThrow(() -> notificationService.onOrderCreated(event, "event-1"));
        // Stored, and then once more when the delivery attempt is recorded
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertEquals("ORDER_CREATED", saved.getAllValues().get(0).getType());
    }

    @Test
    void onOrderCreated_shouldDropRedeliveryOfDeliveredNotification() {
        when(notificationRepository.findByEventId("event-1"))
                .thenReturn(Optional.of(Notification.builder()
                        .id("notif-1")
                        .userId("user-1")
                        .sent(true)
                        .attempts(1)
                        .build()));

        notificationService.onOrderCreated(orderCreated(), "event-1");

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    /**
     * A redelivery of an event whose notification never went out is a free attempt: dropping it
     * would leave the user without the message until the scheduled retry, and before the job
     * existed - forever.
     */
    @Test
    void onOrderCreated_shouldRetryDeliveryOfUnsentNotificationOnRedelivery() {
        Notification stored = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .eventId("event-1")
                .subject("Order Created")
                .body("body")
                .sent(false)
                .attempts(1)
                .lastError("connection refused")
                .build();
        when(notificationRepository.findByEventId("event-1")).thenReturn(Optional.of(stored));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));

        notificationService.onOrderCreated(orderCreated(), "event-1");

        // The stored record is delivered, not replaced by a second one
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(notificationRepository).save(stored);
        assertTrue(stored.isSent(), "the stored notification is the one that went out");
        assertEquals(2, stored.getAttempts());
        assertNull(stored.getLastError());
        verify(emailSender).send(eq("buyer@example.com"), anyString(), anyString());
    }

    /** The attempts are bounded, so a redelivery must not spend a sixth one. */
    @Test
    void onOrderCreated_shouldNotRetryRedeliveryAfterAttemptsRunOut() {
        when(notificationRepository.findByEventId("event-1"))
                .thenReturn(Optional.of(Notification.builder()
                        .id("notif-1")
                        .userId("user-1")
                        .sent(false)
                        .attempts(3)
                        .lastError("mailbox unavailable")
                        .build()));

        notificationService.onOrderCreated(orderCreated(), "event-1");

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void onOrderStatusChanged_shouldCreateNotification() {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setStatus("PAID")
                .build();

        Notification savedNotification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_STATUS_CHANGED")
                .subject("Order Status: PAID")
                .sent(false)
                .build();

        when(notificationRepository.findByEventId("event-2")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(savedNotification);

        assertDoesNotThrow(() -> notificationService.onOrderStatusChanged(event, "event-2"));
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertEquals("ORDER_STATUS_CHANGED", saved.getAllValues().get(0).getType());
    }

    @Test
    void onOrderStatusChanged_shouldStoreEvenWithoutEventId() {
        // A producer that sent no delivery id leaves nothing to match a redelivery on,
        // so the notification is stored rather than silently dropped
        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setStatus("PAID")
                .build();

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.onOrderStatusChanged(event, null);

        // Stored without an event id, then once more when the delivery attempt is recorded
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    private static OrderCreatedEvent orderCreated() {
        return OrderCreatedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setTotalAmount(decimal("99.99"))
                .build();
    }

    /** Same encoding the producer uses for the Avro decimal(19,2) contract. */
    private static ByteBuffer decimal(String value) {
        return ByteBuffer.wrap(new BigDecimal(value).setScale(2).unscaledValue().toByteArray());
    }

    @Test
    void markAsSent_shouldMarkNotificationAsSent() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .type("ORDER_CREATED")
                .sent(false)
                .build();

        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notification));

        assertDoesNotThrow(() -> notificationService.markAsSent("notif-1"));
        assertTrue(notification.isSent());
        assertNotNull(notification.getSentAt());
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void markAsSent_shouldThrowWhenNotFound() {
        when(notificationRepository.findById("999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> notificationService.markAsSent("999"));
    }

    @Test
    void getNotificationsByUserId_shouldReturnList() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .build();
        when(notificationRepository.findByUserId("user-1")).thenReturn(List.of(notification));

        List<Notification> result = notificationService.getNotificationsByUserId("user-1");

        assertEquals(1, result.size());
        assertEquals("user-1", result.get(0).getUserId());
    }

    @Test
    void getUnsentNotificationsByUserId_shouldReturnUnsentOnly() {
        Notification unsent = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .sent(false)
                .build();
        when(notificationRepository.findByUserIdAndSent("user-1", false)).thenReturn(List.of(unsent));

        List<Notification> result = notificationService.getUnsentNotificationsByUserId("user-1");

        assertEquals(1, result.size());
        assertFalse(result.get(0).isSent());
    }

    /**
     * The whole point of the flow: a notification the user is supposed to receive has to reach the
     * mail server, and only then may it be recorded as sent.
     */
    @Test
    void onOrderCreated_shouldSendEmailAndMarkNotificationAsSent() {
        when(notificationRepository.findByEventId("event-1")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));

        notificationService.onOrderCreated(orderCreated(), "event-1");

        // Saved once on creation and once more when the delivery was recorded
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        Notification delivered = saved.getValue();
        assertTrue(delivered.isSent(), "marked sent after delivery");
        assertNotNull(delivered.getSentAt());

        verify(emailSender).send(eq("buyer@example.com"), anyString(), anyString());
    }

    /**
     * The contact projection may lag behind, so an unknown address must leave the notification
     * unsent and readable instead of failing the whole event or pretending it was delivered.
     */
    @Test
    void onOrderCreated_shouldLeaveNotificationUnsentWhenNoAddressIsKnown() {
        when(notificationRepository.findByEventId("event-1")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userContactRepository.findById("user-1")).thenReturn(Optional.empty());

        notificationService.onOrderCreated(orderCreated(), "event-1");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertFalse(saved.getValue().isSent());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    /**
     * The defect this guards: the store and the send were one transaction, so a mail server
     * failure rolled the notification back and left nothing to retry - the event ended on the
     * dead letter topic instead.
     */
    @Test
    void onOrderCreated_shouldKeepTheRecordAndScheduleARetryWhenSendFails() {
        when(notificationRepository.findByEventId("event-1")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));
        doThrow(new RuntimeException("mail server refused the message"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        // Not thrown: the record is durable, so the offset may be acknowledged and the retry job
        // owns the next attempt
        assertDoesNotThrow(() -> notificationService.onOrderCreated(orderCreated(), "event-1"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        Notification kept = saved.getValue();
        assertFalse(kept.isSent(), "a failed send must not be recorded as delivered");
        assertEquals(1, kept.getAttempts());
        assertEquals("mail server refused the message", kept.getLastError());
        assertNotNull(kept.getNextAttemptAt(), "a retry has to be scheduled");
    }

    /** The retry has to stop somewhere: after the last attempt nothing is scheduled any more. */
    @Test
    void deliver_shouldGiveUpAfterTheLastAttempt() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .subject("Order Created")
                .body("body")
                .attempts(2)
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));
        doThrow(new RuntimeException("mailbox unavailable"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        notificationService.deliver(notification);

        assertEquals(3, notification.getAttempts());
        assertNull(notification.getNextAttemptAt(), "no attempt is left, nothing may be scheduled");
        assertFalse(notification.isSent());
        assertEquals("mailbox unavailable", notification.getLastError());
    }

    /** A successful retry clears the schedule and the previous error. */
    @Test
    void deliver_shouldMarkSentAndClearTheScheduleOnSuccess() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .subject("Order Created")
                .body("body")
                .attempts(1)
                .lastError("connection refused")
                .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));

        notificationService.deliver(notification);

        assertTrue(notification.isSent());
        assertNotNull(notification.getSentAt());
        assertNull(notification.getNextAttemptAt());
        assertNull(notification.getLastError());
        verify(notificationRepository).save(notification);
    }

    /** The delay grows with each failure, so a mail server that is down is not hit every minute. */
    @Test
    void deliver_shouldBackOffFurtherAfterEachFailure() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .subject("Order Created")
                .body("body")
                .attempts(1)
                .build();
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));
        doThrow(new RuntimeException("mailbox unavailable"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        LocalDateTime before = LocalDateTime.now();
        notificationService.deliver(notification);

        // Second attempt waits 2x the base delay of 1s
        assertTrue(notification.getNextAttemptAt().isAfter(before.plusNanos(1_500_000_000L)),
                "the second attempt waits longer than the base delay");
    }

    /**
     * Over the limit the message is only moved in time: the record stays unsent, the attempt
     * counter is untouched - otherwise a burst would spend the bounded attempts and the message
     * would be dropped - and the retry job picks it up when a token is back.
     */
    @Test
    void deliver_shouldDeferWhenTheUserIsRateLimited() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .subject("Order Created")
                .body("body")
                .nextAttemptAt(LocalDateTime.now())
                .build();
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));
        when(rateLimiter.tryAcquire("user-1")).thenReturn(false);
        when(rateLimiter.retryDelayMs()).thenReturn(1000L);

        LocalDateTime before = LocalDateTime.now();
        notificationService.deliver(notification);

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
        assertFalse(notification.isSent());
        assertEquals(0, notification.getAttempts(), "a held-back message is not a failed attempt");
        assertNull(notification.getLastError(), "the limit is not an error of the delivery");
        assertNotNull(notification.getNextAttemptAt(), "the message has to be scheduled again");
        assertTrue(notification.getNextAttemptAt().isAfter(before.plusNanos(500_000_000L)),
                "the next attempt waits for a token");
        verify(notificationRepository).save(notification);
    }

    @Test
    void onOrderStatusChanged_shouldSendEmailAndMarkNotificationAsSent() {        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setOrderId("order-1")
                .setUserId("user-1")
                .setOrderNumber("ORD-ABC12345")
                .setStatus("PAID")
                .build();

        when(notificationRepository.findByEventId("event-2")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userContactRepository.findById("user-1"))
                .thenReturn(Optional.of(UserContact.builder()
                        .userId("user-1")
                        .email("buyer@example.com")
                        .build()));

        notificationService.onOrderStatusChanged(event, "event-2");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertTrue(saved.getValue().isSent());
        verify(emailSender).send(eq("buyer@example.com"), anyString(), anyString());
    }
}
