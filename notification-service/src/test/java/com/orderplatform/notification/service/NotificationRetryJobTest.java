package com.orderplatform.notification.service;

import com.orderplatform.notification.model.Notification;
import com.orderplatform.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRetryJobTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationRetryJob job;

    @Test
    void shouldAttemptEveryDueNotification() {
        Notification first = Notification.builder().id("notif-1").build();
        Notification second = Notification.builder().id("notif-2").build();
        when(notificationRepository.findTop50BySentFalseAndNextAttemptAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        job.retryUnsent();

        verify(notificationService).deliver(first);
        verify(notificationService).deliver(second);
    }

    /** Nothing due means no work: the job must not walk the whole collection every minute. */
    @Test
    void shouldDoNothingWhenNothingIsDue() {
        when(notificationRepository.findTop50BySentFalseAndNextAttemptAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        job.retryUnsent();

        verifyNoInteractions(notificationService);
    }

    /** One broken record must not keep the rest of the batch from being attempted. */
    @Test
    void shouldKeepGoingWhenOneNotificationFails() {
        Notification broken = Notification.builder().id("notif-1").build();
        Notification healthy = Notification.builder().id("notif-2").build();
        when(notificationRepository.findTop50BySentFalseAndNextAttemptAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(broken, healthy));
        doThrow(new RuntimeException("unexpected")).when(notificationService).deliver(broken);

        job.retryUnsent();

        verify(notificationService, times(1)).deliver(healthy);
    }

    /** The job is what makes the retry happen at all, so it has to ask for unsent records only. */
    @Test
    void shouldQueryOnlyUnsentNotificationsThatAreDue() {
        when(notificationRepository.findTop50BySentFalseAndNextAttemptAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        job.retryUnsent();

        verify(notificationRepository).findTop50BySentFalseAndNextAttemptAtBefore(any(LocalDateTime.class));
        verify(notificationService, never()).deliver(any(Notification.class));
    }
}
